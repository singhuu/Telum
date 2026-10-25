package dev.telum.launcher

import android.content.Context
import android.content.SharedPreferences

/** Everything Telum remembers. Stored locally; never synced or sent anywhere. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("telum", Context.MODE_PRIVATE)

    init {
        // Older installs already have preferences. Preserve them without interrupting Home.
        if (!sp.contains("onboardingComplete")) {
            sp.edit().putBoolean("onboardingComplete", sp.all.isNotEmpty()).apply()
        }
        // The old three-page flow had no app page. Keep its feature draft, start at apps.
        if (sp.getInt("setupSchema", 0) < 2) {
            val editor = sp.edit().putInt("setupSchema", 2)
            if (onboardingComplete) editor.remove("setupStep") else editor.putInt("setupStep", 0)
            if (!onboardingComplete && sp.all.keys.any { it.startsWith("setup.") }) {
                editor.putBoolean("setupInProgress", true)
                    .location(SetupLocation(place, lat, lon), "setup.")
            }
            editor.apply()
        }
    }

    val onboardingComplete: Boolean get() = sp.getBoolean("onboardingComplete", false)
    val setupInProgress: Boolean get() = sp.getBoolean("setupInProgress", false)
    var setupStep: Int
        get() = sp.getInt("setupStep", 0).coerceIn(0, 3)
        set(value) = sp.edit().putInt("setupStep", value.coerceIn(0, 3)).apply()

    /** Resume an existing draft, or start a separate copy of the current Home settings. */
    fun beginSetup(restart: Boolean = false) {
        if (setupInProgress && !restart) return
        val choices = when {
            onboardingComplete -> SetupChoices(wallpaper, motion, watchingOn, musicOn,
                musicIdleOn, artistPhotos, weatherOn, alwaysShowNames, clock24, fahrenheit, typeface)
            restart -> SetupChoices.RECOMMENDED
            else -> setupDraft()
        }
        val location = if (onboardingComplete) SetupLocation(place, lat, lon) else if (restart) {
            SetupLocation()
        } else setupLocation
        sp.edit().clearSetup().choices(choices, "setup.").location(location, "setup.").apply {
            // A rerun starts from the rows each app shows now.
            if (onboardingComplete) liveRows().forEach { (key, value) -> putString("setup.$key", value) }
        }.putBoolean("setupInProgress", true).putInt("setupStep", 0).apply()
    }

    private fun liveRows() = sp.all.filter { it.key.startsWith("row:") && it.value is String }
        .mapValues { it.value as String }

    /** The draft of [rowChoice] for Live Activities: null keeps the app's default row. */
    fun setupRowChoice(pkg: String): String? = sp.getString("setup.row:$pkg", null)
    fun setSetupRowChoice(pkg: String, choice: String?) =
        sp.edit().apply { if (choice == null) remove("setup.row:$pkg") else putString("setup.row:$pkg", choice) }.apply()

    fun setupApps(): SetupApps? {
        if (!sp.contains("setup.appOrder")) return null
        return SetupApps(sp.getString("setup.appOrder", "")!!.split('|').filter { it.isNotEmpty() },
            sp.getStringSet("setup.appSelected", emptySet())!!.toSet())
    }

    fun saveSetupApps(apps: SetupApps) = sp.edit()
        .putString("setup.appOrder", apps.order.joinToString("|"))
        .putStringSet("setup.appSelected", apps.selected.toSet()).apply()

    val setupLocation: SetupLocation get() = SetupLocation(
        sp.getString("setup.place", "")!!,
        sp.getString("setup.lat", "NaN")!!.toDoubleOrNull() ?: Double.NaN,
        sp.getString("setup.lon", "NaN")!!.toDoubleOrNull() ?: Double.NaN,
    )

    fun saveSetupLocation(location: SetupLocation) = sp.edit().location(location, "setup.").apply()

    fun setupDraft() = SetupChoices(
        wallpaper = sp.getString("setup.wallpaper", "dynamic")!!,
        motion = sp.getBoolean("setup.motion", true),
        watching = sp.getBoolean("setup.watching", true),
        music = sp.getBoolean("setup.music", true),
        musicIdle = sp.getBoolean("setup.musicIdle", true),
        artistPhotos = sp.getBoolean("setup.artistPhotos", true),
        weather = sp.getBoolean("setup.weather", true),
        names = sp.getBoolean("setup.names", false),
        clock24 = sp.getBoolean("setup.clock24", false),
        fahrenheit = sp.getBoolean("setup.fahrenheit", false),
        typeface = sp.getString("setup.typeface", "manrope")!!,
    )

    private fun SharedPreferences.Editor.choices(c: SetupChoices, prefix: String) = apply {
        putString(prefix + "wallpaper", c.wallpaper)
        putBoolean(prefix + "motion", c.motion)
        putBoolean(prefix + "watching", c.watching)
        putBoolean(prefix + "music", c.music)
        putBoolean(prefix + "musicIdle", c.musicIdle)
        putBoolean(prefix + "artistPhotos", c.artistPhotos)
        putBoolean(prefix + "weather", c.weather)
        putBoolean(prefix + "names", c.names)
        putBoolean(prefix + "clock24", c.clock24)
        putBoolean(prefix + "fahrenheit", c.fahrenheit)
        putString(prefix + "typeface", c.typeface)
    }

    private fun SharedPreferences.Editor.location(location: SetupLocation, prefix: String) = apply {
        putString(prefix + "place", location.place)
        putString(prefix + "lat", location.lat.toString())
        putString(prefix + "lon", location.lon.toString())
    }

    private fun SharedPreferences.Editor.clearSetup() = apply {
        sp.all.keys.filter { it.startsWith("setup.") }.forEach { remove(it) }
        remove("setupStep")
        remove("setupInProgress")
    }

    fun saveSetupDraft(choices: SetupChoices) = sp.edit().choices(choices, "setup.").apply()

    fun finishSetup(choices: SetupChoices, apps: SetupApps) {
        val location = setupLocation
        val changedLocation = location != SetupLocation(place, lat, lon)
        val presented = apps.order.toSet()
        val hiddenAfterSetup = (hidden - presented) + (presented - apps.selected)
        sp.edit().choices(choices, "").location(location, "")
            .putString("order", apps.order.distinct().joinToString("|"))
            .putStringSet("hidden", hiddenAfterSetup)
            .putBoolean("onboardingComplete", true).apply {
                if (changedLocation) {
                    remove("weatherJson")
                    remove("weatherAt")
                }
                // The draft holds every row choice, so it replaces the live ones.
                liveRows().keys.forEach { remove(it) }
                sp.all.forEach { (key, value) ->
                    if (key.startsWith("setup.row:") && value is String) putString(key.removePrefix("setup."), value)
                }
            }.clearSetup().apply()
    }

    fun cancelSetup() = sp.edit().clearSetup().apply()

    var order: List<String>
        get() = sp.getString("order", null)?.split('|')?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString("order", v.joinToString("|")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** "dynamic" follows the focused app; anything else is a fixed theme. */
    var wallpaper: String
        get() = sp.getString("wallpaper", "dynamic")!!
        set(v) = sp.edit().putString("wallpaper", v).apply()

    /** One of [Fonts.CHOICES]. */
    var typeface: String
        get() = sp.getString("typeface", "manrope")!!
        set(v) = sp.edit().putString("typeface", v).apply()

    /** Show what you're watching: rows other apps publish for the TV home screen. */
    var watchingOn: Boolean
        get() = sp.getBoolean("watching", false)
        set(v) = sp.edit().putBoolean("watching", v).apply()

    /** Music controls, once notification access is granted. */
    var musicOn: Boolean
        get() = sp.getBoolean("music", true)
        set(v) = sp.edit().putBoolean("music", v).apply()

    var musicIdleOn: Boolean
        get() = sp.getBoolean("musicIdle", true)
        set(v) = sp.edit().putBoolean("musicIdle", v).apply()

    /** Full-screen artist photos for music, looked up by artist name on TheAudioDB. Off unless chosen. */
    var artistPhotos: Boolean
        get() = sp.getBoolean("artistPhotos", false)
        set(v) = sp.edit().putBoolean("artistPhotos", v).apply()

    /** Which of an app's rows the hero shows: null automatic (a row with progress), "off", or the row's name. */
    fun rowChoice(pkg: String): String? = sp.getString("row:$pkg", null)
    fun setRowChoice(pkg: String, choice: String?) =
        sp.edit().apply { if (choice == null) remove("row:$pkg") else putString("row:$pkg", choice) }.apply()

    var motion: Boolean
        get() = sp.getBoolean("motion", true)
        set(v) = sp.edit().putBoolean("motion", v).apply()

    /** The intro's hum. On unless turned off in settings. */
    var introSound: Boolean
        get() = sp.getBoolean("introSound", true)
        set(v) = sp.edit().putBoolean("introSound", v).apply()

    var alwaysShowNames: Boolean
        get() = sp.getBoolean("names", false)
        set(v) = sp.edit().putBoolean("names", v).apply()

    var clock24: Boolean
        get() = sp.getBoolean("clock24", false)
        set(v) = sp.edit().putBoolean("clock24", v).apply()

    var weatherOn: Boolean
        get() = sp.getBoolean("weather", false)
        set(v) = sp.edit().putBoolean("weather", v).apply()

    var fahrenheit: Boolean
        get() = sp.getBoolean("fahrenheit", false)
        set(v) = sp.edit().putBoolean("fahrenheit", v).apply()

    var place: String
        get() = sp.getString("place", "")!!
        set(v) = sp.edit().putString("place", v).apply()

    var lat: Double
        get() = sp.getString("lat", "NaN")!!.toDouble()
        set(v) = sp.edit().putString("lat", v.toString()).apply()

    var lon: Double
        get() = sp.getString("lon", "NaN")!!.toDouble()
        set(v) = sp.edit().putString("lon", v.toString()).apply()

    var weatherJson: String?
        get() = sp.getString("weatherJson", null)
        set(v) = sp.edit().putString("weatherJson", v).apply()

    var weatherAt: Long
        get() = sp.getLong("weatherAt", 0L)
        set(v) = sp.edit().putLong("weatherAt", v).apply()

    var hintsShown: Int
        get() = sp.getInt("hints", 0)
        set(v) = sp.edit().putInt("hints", v).apply()

    /** The boot (Settings.Global.BOOT_COUNT) whose first start played the intro. */
    var introBoot: Int
        get() = sp.getInt("introBoot", -2)
        set(v) = sp.edit().putInt("introBoot", v).apply()

    fun lastOpened(key: String): Long = sp.getLong("t:$key", 0L)
    fun markOpened(key: String) = sp.edit().putLong("t:$key", System.currentTimeMillis()).apply()
}
