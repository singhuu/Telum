package dev.telum.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.EditText
import android.app.ActivityOptions

class MainActivity : Activity(), HomeView.Host {

    private companion object {
        const val STOCK_HOME = "com.google.android.tvlauncher"
        const val REQUEST_TV_LISTINGS = 1
    }

    private lateinit var prefs: Prefs
    private lateinit var apps: Apps
    private lateinit var weather: Weather
    private lateinit var content: Content
    private lateinit var media: Media
    private lateinit var home: HomeView
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Minute tick, clock change or time zone change: rebuild the cached clock strings.
            home.textsChanged()
            if (i.action == Intent.ACTION_TIME_TICK) weather.refreshIfStale { home.textsChanged(); home.refreshPages() }
        }
    }

    private val packages = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }
    private var pendingReload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        if (handOverToHome(intent)) return
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        prefs = Prefs(this)
        apps = Apps(this, prefs)
        weather = Weather(prefs)
        content = Content(this, prefs)
        media = Media(this, prefs)
        home = HomeView(this, this, prefs)
        setContentView(home)
        home.requestFocus()
        // The first load starts in onStart, which always follows.
        (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).registerCallback(packages, main)
        if (prefs.hintsShown < 3) {
            prefs.hintsShown = prefs.hintsShown + 1
            main.postDelayed({ home.toast("Hold OK on an app to move or hide it") }, 1400L)
        }
    }

    /**
     * When Telum is the default home but was opened as an ordinary app (from Settings, or by
     * adb), Android would keep a second, separate instance alive next to the home one (seen on
     * the SHIELD: two activities, double the memory). Hand over to Home and close this one.
     */
    private fun handOverToHome(i: Intent?): Boolean {
        if (i?.hasCategory(Intent.CATEGORY_HOME) == true) return false
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (home?.activityInfo?.packageName != packageName) return false
        startActivity(homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        return true
    }

    override fun onDestroy() {
        if (!::home.isInitialized) {
            // Handed over to the home instance in onCreate; nothing was set up.
            super.onDestroy()
            return
        }
        (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).unregisterCallback(packages)
        main.removeCallbacksAndMessages(null)
        apps.close()
        weather.close()
        content.close()
        media.stop()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        val incomplete = apps.all.isEmpty() || apps.all.any { it.art == null }
        if (pendingReload || (incomplete && !apps.loading)) reload()
        weather.refreshIfStale { home.textsChanged(); home.refreshPages() }
        // Rows from other apps, and what they are playing, are followed only while Home is in front.
        content.start { home.contentChanged() }
        media.start { home.mediaChanged() }
    }

    override fun onResume() {
        super.onResume()
        home.setActive(true)
    }

    override fun onPause() {
        home.setActive(false)
        super.onPause()
    }

    override fun onStop() {
        started = false
        try { unregisterReceiver(tick) } catch (_: IllegalArgumentException) {}
        content.stop()
        media.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while Telum is already in front.
        if (Intent.ACTION_MAIN == intent.action && hasWindowFocus()) home.goHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // The home screen never closes itself; HomeView handles Back.
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::home.isInitialized) return
        // Only when Android is about to kill the process anyway: drop the artwork (about 4 MB)
        // and ask for a collection so its native memory is returned now. Trimming at milder
        // levels, as before, made every playback session discard and reload the artwork, and
        // on the SHIELD the discarded bitmaps piled up in the native heap (12 -> 73 MB) because
        // Telum's tiny Java heap rarely triggers a collection.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE && !started) {
            apps.trim()
            home.dropArt()
            pendingReload = true
            System.gc()
        }
    }

    private fun reload() {
        if (!started && apps.all.isNotEmpty()) {
            pendingReload = true
            return
        }
        pendingReload = false
        apps.load(
            onList = { home.setApps(apps.visible()) },
            onArt = { e -> home.artChanged(e) },
        )
    }

    // ------------------------------------------------------------------ Host

    override fun launch(entry: AppEntry, bounds: Rect) {
        val intent = apps.launchIntent(entry) ?: return
        val opts = ActivityOptions.makeScaleUpAnimation(home, bounds.left, bounds.top, bounds.width(), bounds.height())
        try {
            startActivity(intent, opts.toBundle())
        } catch (e: ActivityNotFoundException) {
            home.toast("${entry.label} can't be opened")
        } catch (e: SecurityException) {
            home.toast("${entry.label} can't be opened")
        }
    }

    override fun appInfo(entry: AppEntry) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", entry.pkg, null)))
    }

    override fun system(action: String) {
        when (action) {
            "credits" -> {
                val dialog = AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
                    .setTitle("Credits and privacy")
                    .setMessage("Telum source: GPL-3.0-only.\n\nInter and Manrope fonts: SIL Open Font License 1.1.\n\nWeather by Open-Meteo, CC BY 4.0. Enabling weather sends your chosen city and coordinates to Open-Meteo.\nhttps://open-meteo.com/\nhttps://creativecommons.org/licenses/by/4.0/\n\nOptional artist photos by TheAudioDB. Enabling them sends artist names to TheAudioDB and downloads images from its image hosts.\nhttps://www.theaudiodb.com/\n\nApp artwork comes from installed apps and the addresses they publish. Image hosts receive your IP address and image request. Notification access is used for media sessions; Telum does not store notification contents. No analytics or Telum account.")
                    .setPositiveButton("Close", null).show()
                dialog.findViewById<android.widget.TextView>(android.R.id.message)?.let {
                    android.text.util.Linkify.addLinks(it, android.text.util.Linkify.WEB_URLS)
                    it.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                }
            }
            "search" -> {
                // The same path as the remote's mic button.
                if (!onSearchRequested()) start(Intent(Intent.ACTION_ASSIST))
            }
            "notifications" -> start(Intent("com.android.tv.action.OPEN_NOTIFICATIONS_PANEL"), fallback = Intent(Settings.ACTION_SETTINGS))
            "screensaver" -> startScreensaver()
            "wifi" -> start(Intent(Settings.ACTION_WIFI_SETTINGS))
            "display" -> start(Intent("android.settings.DISPLAY_SETTINGS"), fallback = Intent(Settings.ACTION_SETTINGS))
            "remotes" -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "apps" -> start(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            "settings" -> start(Intent(Settings.ACTION_SETTINGS))
            "stock-home" -> start(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(STOCK_HOME, "$STOCK_HOME.MainActivity")))
        }
    }

    private fun startScreensaver() {
        // SystemUI's "Somnambulator" starts the configured screensaver right away.
        val dream = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
        start(dream, fallback = Intent(Settings.ACTION_DREAM_SETTINGS))
    }

    private fun start(intent: Intent, fallback: Intent? = null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (fallback != null) start(fallback) else home.toast("Not available on this device")
        }
    }

    override fun saveOrder(entries: List<AppEntry>) = apps.saveOrder(entries)

    override fun hide(entry: AppEntry) {
        apps.setHidden(entry.key, true)
        home.setApps(apps.visible())
        home.toast("${entry.label} hidden. Find it in Control center › Telum")
    }

    override fun unhide(key: String) {
        apps.setHidden(key, false)
        home.setApps(apps.visible())
        home.refreshPages()
    }

    override fun allApps(): List<AppEntry> = apps.all

    override fun editPlace() {
        // Built with the dialog's theme so its underline and cursor match the white accent.
        val input = EditText(ContextThemeWrapper(this, R.style.Theme_Telum_Dialog)).apply {
            setText(prefs.place)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
            .setTitle("Weather location")
            .setMessage("Enter a city. The search is sent to Open-Meteo.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val q = input.text.toString()
                if (q.isBlank()) return@setPositiveButton
                weather.geocode(q) { r ->
                    if (r == null) {
                        home.toast("Couldn't find \"$q\"")
                    } else {
                        prefs.place = r.first; prefs.lat = r.second; prefs.lon = r.third
                        weather.clear()
                        weather.refreshIfStale(force = true) { home.textsChanged(); home.refreshPages() }
                        home.refreshPages()
                        home.toast("Weather for ${r.first}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun content(): Content = content

    override fun media(): Media = media

    /**
     * Music controls need notification access, which only the user can grant in Settings. Android
     * TV has no direct link to that screen on every device, so try the direct ones, then open
     * Apps and say where to go.
     */
    override fun setMusicControls(on: Boolean) {
        if (!on) {
            prefs.musicOn = false
            media.clear()
            home.mediaChanged()
            home.refreshPages()
            return
        }
        prefs.musicOn = true
        media.start { home.mediaChanged() }
        if (!media.granted()) {
            val listener = ComponentName(this, MediaListener::class.java)
            val candidates = listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString()) else null,
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_SETTINGS),
            )
            val direct = candidates.firstOrNull { packageManager.resolveActivity(it, 0) != null } ?: Intent(Settings.ACTION_SETTINGS)
            start(direct)
            if (direct.action == Settings.ACTION_APPLICATION_SETTINGS || direct.action == Settings.ACTION_SETTINGS) {
                android.widget.Toast.makeText(this, "Open Special app access › Notification access, then turn on Telum music controls", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        home.refreshPages()
    }

    /**
     * Opens a programme through the link its app published. The link must open that same app;
     * anything else (or a broken link) opens the app itself instead.
     */
    override fun openProgramme(pkg: String, programme: Programme) {
        val intent = programme.intent?.let { try { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) } catch (_: Exception) { null } }
        if (intent != null) {
            intent.selector = null
            intent.flags = (intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK) and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
            val target = packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            if (target == pkg) {
                try { startActivity(intent); return } catch (_: Exception) {}
            }
        }
        val app = apps.all.firstOrNull { it.pkg == pkg }
        val launch = app?.let { apps.launchIntent(it) }
        if (launch != null) start(launch) else home.toast("That can't be opened")
    }

    override fun setWatching(on: Boolean) {
        if (!on) {
            prefs.watchingOn = false
            content.clear()
            home.contentChanged()
            return
        }
        content.permissionChanged()
        if (content.permitted()) {
            prefs.watchingOn = true
            content.start { home.contentChanged() }
            home.refreshPages()
        } else {
            // Android's own prompt: "Allow Telum to read all TV listings available on your device?"
            requestPermissions(arrayOf(Content.PERMISSION), REQUEST_TV_LISTINGS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_TV_LISTINGS || !::home.isInitialized) return
        content.permissionChanged()
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            prefs.watchingOn = true
            content.start { home.contentChanged() }
        } else {
            home.toast("Telum needs that permission to show what you're watching")
        }
        home.refreshPages()
    }

    override fun settingsChanged(what: String) {
        when (what) {
            "wallpaper" -> home.applyWallpaper()
            "typeface" -> home.applyTypeface()
            "motion" -> home.applyMotion()
            "rows" -> home.contentChanged()
            "weather" -> if (prefs.weatherOn) {
                if (prefs.place.isBlank()) editPlace()
                else weather.refreshIfStale(force = true) { home.textsChanged(); home.refreshPages() }
            }
        }
        home.textsChanged()
    }

    override fun weather(): WeatherNow? = if (prefs.weatherOn) weather.now else null

    override fun reloadApps() = reload()

    override fun stockHomeEnabled(): Boolean = try {
        packageManager.getApplicationInfo(STOCK_HOME, 0).enabled
    } catch (_: Exception) {
        false
    }

    @Volatile private var memory = "v${BuildConfig.VERSION_NAME}"

    override fun memoryLine(): String {
        // Reading process memory walks /proc (tens of milliseconds on the SHIELD), so it runs off
        // the main thread and the settings row picks the figure up when it is ready.
        Thread {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val line = "v${BuildConfig.VERSION_NAME} · ${info.totalPss / 1024} MB"
            main.post { memory = line; if (::home.isInitialized) home.kick() }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
        return memory
    }

    override fun memoryText(): String = memory
}
