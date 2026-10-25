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
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.app.ActivityOptions
import android.app.NotificationManager

class MainActivity : Activity(), HomeView.Host {

    private companion object {
        const val STOCK_HOME = "com.google.android.tvlauncher"
        const val REQUEST_TV_LISTINGS = 1
        const val REQUEST_HOME = 2
        const val REQUEST_HOME_SETTINGS = 3
        /** Plays the intro over Home, for testing: am start ... --ez dev.telum.launcher.extra.INTRO true */
        const val EXTRA_INTRO = "dev.telum.launcher.extra.INTRO"
        /** Asks to become Home, as setup does at the end, for testing: --ez dev.telum.launcher.extra.ASK_HOME true */
        const val EXTRA_ASK_HOME = "dev.telum.launcher.extra.ASK_HOME"
        /**
         * Android's Home request answered faster than this never showed its dialog: the device
         * doesn't offer one, or the user once chose "Don't ask again". Nobody reads and answers
         * a dialog this fast.
         */
        const val UNSEEN_REFUSAL_MS = 500L

        /**
         * When Telum last asked Android to become Home (uptime), or 0. Shared by both instances:
         * once the user picks Telum, Android starts Home itself, and that new Home instance is
         * the one that says so.
         */
        var homeAskedAt = 0L
    }

    private lateinit var prefs: Prefs
    private lateinit var apps: Apps
    private lateinit var weather: Weather
    private lateinit var content: Content
    private lateinit var media: Media
    private lateinit var home: HomeView
    private var onboarding: OnboardingView? = null
    /** Holds the page (Home or setup) and, above it while it plays, the intro. */
    private lateinit var stage: FrameLayout
    private var page: View? = null
    private var intro: IntroView? = null
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private val defaultHome = DefaultHome(this)

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (onboarding != null) return
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
        stage = FrameLayout(this)
        setContentView(stage)
        // The theme's window is black, so a cold start (at boot, the intro's) begins on true black.
        // Pages fade in over the navy backdrop, which playIntro holds back while the intro plays.
        window.setBackgroundDrawableResource(R.drawable.window_bg)
        val bootIntro = firstStartSinceBoot() || intent.getBooleanExtra(EXTRA_INTRO, false)
        if (prefs.onboardingComplete && !prefs.setupInProgress) {
            show(home)
            home.requestFocus()
        } else {
            openOnboarding()
        }
        // The first load starts in onStart, which always follows.
        (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).registerCallback(packages, main)
        val setup = onboarding
        when {
            setup == null && bootIntro -> playHomeIntro()
            setup == null && justBecameHome() -> becameHome()
            setup == null && intent.getBooleanExtra(EXTRA_ASK_HOME, false) -> askToBeHome()
            setup == null -> showHomeHint()
            // A setup resumed after a restart: openOnboarding played nothing.
            setup != null && bootIntro && intro == null -> playIntro(prefs.setupDraft().motion, setup)
        }
    }

    private fun show(view: View) {
        page?.let { stage.removeView(it) }
        page = view
        stage.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** True for Telum's first start after the device boots, once per boot. */
    private fun firstStartSinceBoot(): Boolean {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot == prefs.introBoot) return false
        prefs.introBoot = boot
        return true
    }

    private fun playHomeIntro() = playIntro(prefs.motion, home, reveal = {
        home.alpha = 0f
        home.animate().alpha(1f).setDuration(if (prefs.motion) 900L else 300L).start()
    }, done = { showHomeHint() })

    /**
     * Plays the intro over [under], which stays hidden (and undrawn) until the intro starts to
     * dissolve. The window is black while it plays, so it starts from true black even while its
     * shaders compile, and the navy backdrop fades back in under the dissolve. Any key skips it.
     * Devices without OpenGL ES 3.0 go straight on.
     */
    private fun playIntro(motion: Boolean, under: View, reveal: () -> Unit = {}, done: () -> Unit = {}) {
        if (intro != null) return
        if (!IntroView.supported(this)) { done(); return }
        under.visibility = View.INVISIBLE
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val sound = if (prefs.introSound) IntroSound(this, motion) else null
        val view = IntroView(this, motion, sound, onReveal = {
            under.visibility = View.VISIBLE
            val backdrop = TransitionDrawable(arrayOf(ColorDrawable(Color.BLACK), getDrawable(R.drawable.window_bg)))
            backdrop.isCrossFadeEnabled = true
            window.setBackgroundDrawable(backdrop)
            backdrop.startTransition(500)
            reveal()
        }, onDone = {
            intro?.let { stage.removeView(it) }
            intro = null
            if (page === under) under.requestFocus()
            done()
        })
        intro = view
        stage.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playing = intro ?: return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE ->
                return super.dispatchKeyEvent(event)
        }
        // Any other press skips it, and nothing reaches the page until it has gone.
        if (event.action == KeyEvent.ACTION_UP) playing.skip()
        return true
    }

    private fun openOnboarding(restart: Boolean = false) {
        val resuming = prefs.setupInProgress && !restart
        prefs.beginSetup(restart)
        home.setActive(false)
        content.stop()
        media.stop()
        onboarding = OnboardingView(this, prefs, object : OnboardingView.Host {
                override fun musicReady() = getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(ComponentName(this@MainActivity, MediaListener::class.java))
                override fun watchingReady() = checkSelfPermission(Content.PERMISSION) == PackageManager.PERMISSION_GRANTED
                override fun connectMusic() = requestMusicAccess()
                override fun connectWatching() {
                    if (!watchingReady()) requestPermissions(arrayOf(Content.PERMISSION), REQUEST_TV_LISTINGS)
                }
                override fun chooseCity() = editPlace()
                override fun retryApps() = reload()
                override fun musicSteps() = when (musicAccessIntent().action) {
                    Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS -> listOf(
                        "Turn on Telum music controls.", "Press Back to return here.")
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS -> listOf(
                        "Select Telum music controls.", "Turn it on, then select Allow.", "Press Back to return here.")
                    // The SHIELD: TV Settings has no direct link to notification access.
                    else -> listOf("Select Special app access.", "Select Notification access.",
                        "Turn on Telum music controls, then select Allow.", "Press Back to return here.")
                }
                override fun previewActivities(result: (Map<String, List<Shelf>>) -> Unit) = content.preview(result)
                override fun finish(choices: SetupChoices, selection: SetupApps) = finishOnboarding(choices, selection)
                override fun exit() {
                    prefs.cancelSetup()
                    home.setApps(apps.visible())
                    pendingReload = true
                    showHomeAfterSetup()
                }
            }, showIntro = !resuming)
        show(onboarding!!)
        if (started) reload()
        if (!resuming) playIntro(prefs.setupDraft().motion, onboarding!!, reveal = { onboarding?.replayTitle() })
    }

    private fun showHomeHint() {
        if (prefs.hintsShown < 3) {
            prefs.hintsShown = prefs.hintsShown + 1
            main.postDelayed({ home.toast("Hold OK on an app to move or hide it") }, 1400L)
        }
    }

    private fun finishOnboarding(choices: SetupChoices, selection: SetupApps) {
        prefs.finishSetup(choices, selection)
        weather.clear()
        val byKey = apps.all.associateBy { it.key }
        home.setApps(selection.order.filter { it in selection.selected }.mapNotNull { byKey[it] })
        pendingReload = true
        showHomeAfterSetup()
        // Unless Telum already is Home, ask once Home has faded in. The hint waits for the answer.
        if (defaultHome.isTelum()) showHomeHint()
        else main.postDelayed({ askToBeHome() }, if (prefs.motion) 900L else 300L)
    }

    // ------------------------------------------------------------------ default Home

    /** The pop-up at the end of setup: asks to become the remote's Home. */
    private fun askToBeHome() {
        if (isFinishing || isDestroyed || onboarding != null || defaultHome.isTelum()) return
        val dialog = AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
            .setTitle("Make Telum your Home?")
            .setMessage("Your remote's Home button will open Telum.")
            .setPositiveButton("Make Telum Home") { _, _ -> makeHome() }
            .setNegativeButton("Not now") { _, _ -> showHomeHint() }
            .setOnCancelListener { showHomeHint() }
            .create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus() }
        dialog.show()
    }

    /** Hands over to the screen this device offers: Android's request dialog, else its Home list. */
    private fun makeHome() {
        val request = defaultHome.requestIntent()
        when {
            // Asking again would change nothing: Telum has the role, and the stock Home outranks it.
            defaultHome.state() == DefaultHome.State.OUTRANKED -> homeChanged()
            request != null -> try {
                homeAskedAt = SystemClock.uptimeMillis()
                startActivityForResult(request, REQUEST_HOME)
            } catch (_: ActivityNotFoundException) {
                openHomeSettings()
            }
            else -> openHomeSettings()
        }
    }

    private fun openHomeSettings() {
        val settings = defaultHome.settingsIntent() ?: return homeNotOffered()
        try {
            homeAskedAt = SystemClock.uptimeMillis()
            startActivityForResult(settings, REQUEST_HOME_SETTINGS)
        } catch (_: ActivityNotFoundException) {
            homeNotOffered()
        }
    }

    /** After Android's dialog or Home list: says where the Home button goes now. */
    private fun homeChanged() {
        when (defaultHome.state()) {
            // Opened from another launcher, this is not the Home instance; hand over to it.
            DefaultHome.State.TELUM -> if (!handOverToHome(intent) && justBecameHome()) becameHome()
            DefaultHome.State.OUTRANKED -> {
                homeAskedAt = 0L
                AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
                    .setTitle("${defaultHome.currentName() ?: "Your TV's Home"} still opens first")
                    .setMessage("Your TV puts its built-in Home ahead of other apps. To change that, see “Select Telum as Home” in Telum's install guide.")
                    .setPositiveButton("OK", null).show()
            }
            DefaultHome.State.OTHER -> homeAskedAt = 0L // The user said no.
        }
    }

    /** True once, in the instance that first sees Telum as Home after it asked. */
    private fun justBecameHome(): Boolean {
        val asked = homeAskedAt
        if (asked == 0L || SystemClock.uptimeMillis() - asked > 120_000L || !defaultHome.isTelum()) return false
        homeAskedAt = 0L
        return true
    }

    private fun becameHome() = main.postDelayed({ home.toast("Telum is now your Home") }, 600L)

    private fun homeNotOffered() {
        AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
            .setTitle("Your TV can't switch Home from here")
            .setMessage("See “Select Telum as Home” in Telum's install guide.")
            .setPositiveButton("OK", null).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!::home.isInitialized) return
        when (requestCode) {
            REQUEST_HOME -> {
                val unseen = resultCode != RESULT_OK && SystemClock.uptimeMillis() - homeAskedAt < UNSEEN_REFUSAL_MS
                if (unseen && defaultHome.state() == DefaultHome.State.OTHER) openHomeSettings() else homeChanged()
            }
            REQUEST_HOME_SETTINGS -> homeChanged()
        }
    }

    private fun showHomeAfterSetup() {
        onboarding = null
        home.applyWallpaper()
        home.applyTypeface()
        home.applyMotion()
        home.textsChanged()
        home.refreshPages()
        home.alpha = 0f
        show(home)
        home.requestFocus()
        if (started) startHome()
        home.setActive(hasWindowFocus())
        home.animate().alpha(1f).setDuration(if (prefs.motion) 800L else 200L).start()
    }

    /**
     * When Telum is the default home but was opened as an ordinary app (from Settings, or by
     * adb), Android would keep a second, separate instance alive next to the home one (seen on
     * the SHIELD: two activities, double the memory). Hand over to Home and close this one.
     *
     * Close first: while this single-task activity is still on top, Android hands the Home
     * intent to it instead of starting the Home instance, and when no Home instance was running
     * the previous launcher's leftover screen came up (emulator, 2026-10-24).
     */
    private fun handOverToHome(i: Intent?): Boolean {
        if (i?.hasCategory(Intent.CATEGORY_HOME) == true) return false
        if (!defaultHome.isTelum()) return false
        finish()
        startActivity(DefaultHome.intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        if (!::home.isInitialized) return
        started = true
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        if (onboarding == null) startHome() else reload()
    }

    private fun startHome() {
        val incomplete = apps.all.isEmpty() || apps.all.any { it.art == null }
        if (pendingReload || (incomplete && !apps.loading)) reload()
        weather.refreshIfStale { home.textsChanged(); home.refreshPages() }
        // Rows from other apps, and what they are playing, are followed only while Home is in front.
        content.start { home.contentChanged() }
        media.start { home.mediaChanged() }
    }

    override fun onResume() {
        super.onResume()
        if (!::home.isInitialized) return
        onboarding?.refreshConnections()
        home.setActive(onboarding == null)
    }

    override fun onPause() {
        if (::home.isInitialized) home.setActive(false)
        // The intro only plays while Telum is in front.
        intro?.cancel()
        super.onPause()
    }

    override fun onStop() {
        if (!::home.isInitialized) { super.onStop(); return }
        started = false
        try { unregisterReceiver(tick) } catch (_: IllegalArgumentException) {}
        content.stop()
        media.stop()
        // Opened from another launcher, then made Home: Android has started the Home instance,
        // so this one would only be a second copy in memory.
        if (!intent.hasCategory(Intent.CATEGORY_HOME) && defaultHome.isTelum()) finish()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed during the intro skips it.
        intro?.let { it.skip(); return }
        if (::home.isInitialized && onboarding == null && intent.getBooleanExtra(EXTRA_INTRO, false)) {
            playHomeIntro()
            return
        }
        if (::home.isInitialized && onboarding == null && intent.getBooleanExtra(EXTRA_ASK_HOME, false)) {
            askToBeHome()
            return
        }
        // Android starting Home right after the user picked Telum.
        if (::home.isInitialized && onboarding == null && justBecameHome()) {
            becameHome()
            return
        }
        // Home pressed while Telum is already in front.
        if (::home.isInitialized && onboarding == null && Intent.ACTION_MAIN == intent.action && hasWindowFocus()) home.goHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onboarding?.back()
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
            onList = {
                val setup = onboarding
                if (setup != null) setup.appsChanged(apps.all) else home.setApps(apps.visible())
            },
            onArt = { e ->
                val setup = onboarding
                if (setup != null) setup.artChanged(apps.all) else home.artChanged(e)
            },
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
            "onboarding" -> openOnboarding(restart = true)
            "default-home" -> makeHome()
            "credits" -> {
                val dialog = AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
                    .setTitle("Credits and privacy")
                    .setMessage("Telum source: GPL-3.0-only.\n\nInter and Manrope fonts: SIL Open Font License 1.1.\n\nSetup's tick icons: Material Symbols by Google, Apache License 2.0.\n\nWeather by Open-Meteo, CC BY 4.0. Enabling weather sends your chosen city and coordinates to Open-Meteo.\nhttps://open-meteo.com/\nhttps://creativecommons.org/licenses/by/4.0/\n\nOptional artist photos by TheAudioDB. Enabling them sends artist names to TheAudioDB and downloads images from its image hosts.\nhttps://www.theaudiodb.com/\n\nApp artwork comes from installed apps and the addresses they publish. Image hosts receive your IP address and image request. Notification access is used for media sessions; Telum does not store notification contents. No analytics or Telum account.")
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
        home.toast("${entry.label} hidden. Find it in Control centre › Telum")
    }

    override fun unhide(key: String) {
        apps.setHidden(key, false)
        home.setApps(apps.visible())
        home.refreshPages()
    }

    override fun allApps(): List<AppEntry> = apps.all

    override fun editPlace() {
        val setup = onboarding
        // Built with the dialog's theme so its underline and cursor match the white accent.
        val input = EditText(ContextThemeWrapper(this, R.style.Theme_Telum_Dialog)).apply {
            setText(if (setup != null) prefs.setupLocation.place else prefs.place)
            hint = "Enter a city"
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_Telum_Dialog)
            .setTitle("Choose your weather city")
            .setMessage("Your search is sent to Open-Meteo.")
            .setView(input)
            .setPositiveButton("Find city", null)
            .setNegativeButton("Cancel", null)
            .create()
        fun hideKeyboard() = getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        dialog.setOnDismissListener { hideKeyboard() }
        dialog.setOnShowListener {
            val find = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            // The keyboard's Enter key closes the keyboard, then searches. With nothing typed it
            // moves to Cancel, so leaving without a city is one press away.
            input.setOnEditorActionListener { _, action, event ->
                val enter = action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
                if (!enter) return@setOnEditorActionListener false
                hideKeyboard()
                if (input.text.isBlank()) cancel.requestFocus() else find.performClick()
                true
            }
            find.setOnClickListener {
                val q = input.text.toString().trim()
                if (q.isBlank()) { input.error = "Enter a city"; return@setOnClickListener }
                hideKeyboard()
                find.isEnabled = false
                dialog.setMessage("Finding your city…")
                weather.geocode(q) { r ->
                    if (!dialog.isShowing || onboarding !== setup || isDestroyed) return@geocode
                    find.isEnabled = true
                    if (r == null) {
                        dialog.setMessage("We couldn't find that city. Check the spelling or your connection, then try again.")
                        find.requestFocus()
                    } else {
                        if (setup != null) {
                            prefs.saveSetupLocation(SetupLocation(r.first, r.second, r.third))
                            setup.refreshConnections()
                        } else {
                            prefs.place = r.first; prefs.lat = r.second; prefs.lon = r.third
                            weather.clear()
                            weather.refreshIfStale(force = true) { home.textsChanged(); home.refreshPages() }
                            home.refreshPages()
                            home.toast("Weather for ${r.first}")
                        }
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
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
        if (!media.granted()) requestMusicAccess()
        home.refreshPages()
    }

    /** The closest screen to Telum's notification-access switch that this device offers. */
    private fun musicAccessIntent(): Intent {
        val listener = ComponentName(this, MediaListener::class.java)
        val candidates = listOfNotNull(
            if (android.os.Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString()) else null,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
        )
        return candidates.firstOrNull { packageManager.resolveActivity(it, 0) != null } ?: Intent(Settings.ACTION_SETTINGS)
    }

    private fun requestMusicAccess() {
        val direct = musicAccessIntent()
        start(direct)
        // Setup shows these steps on screen; Home has only this toast.
        if (onboarding == null && (direct.action == Settings.ACTION_APPLICATION_SETTINGS || direct.action == Settings.ACTION_SETTINGS)) {
            android.widget.Toast.makeText(this, "Open Special app access › Notification access, then turn on Telum music controls", android.widget.Toast.LENGTH_LONG).show()
        }
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
        if (onboarding != null) {
            onboarding?.refreshConnections()
            return
        }
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            prefs.watchingOn = true
            content.start { home.contentChanged() }
        } else {
            home.toast("Live Activities needs TV listings access")
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

    override fun isDefaultHome(): Boolean = defaultHome.isTelum()

    override fun stockHomeEnabled(): Boolean = try {
        packageManager.getApplicationInfo(STOCK_HOME, 0).enabled
    } catch (_: Exception) {
        false
    }

    // "Android TV Home" on the SHIELD.
    override fun stockHomeName(): String = defaultHome.homeName(STOCK_HOME) ?: "TV home"

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
