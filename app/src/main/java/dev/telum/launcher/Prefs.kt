package dev.telum.launcher

import android.content.Context
import android.content.SharedPreferences

/** Everything Telum remembers. Stored locally; never synced or sent anywhere. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("telum", Context.MODE_PRIVATE)

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

    fun lastOpened(key: String): Long = sp.getLong("t:$key", 0L)
    fun markOpened(key: String) = sp.edit().putLong("t:$key", System.currentTimeMillis()).apply()
}
