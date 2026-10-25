package dev.telum.launcher

/** A setup draft. Applying a preset never changes the running launcher. */
data class SetupChoices(
    val wallpaper: String = "dynamic",
    val motion: Boolean = true,
    val watching: Boolean = true,
    val music: Boolean = true,
    val musicIdle: Boolean = true,
    val artistPhotos: Boolean = true,
    val weather: Boolean = true,
    val names: Boolean = false,
    val clock24: Boolean = false,
    val fahrenheit: Boolean = false,
    val typeface: String = "manrope",
) {
    val presetName: String get() = when (this) {
        LIGHT -> "Light"
        RECOMMENDED -> "Recommended"
        else -> "Custom"
    }

    companion object {
        val RECOMMENDED = SetupChoices()
        val LIGHT = SetupChoices(wallpaper = "graphite", motion = false, watching = false,
            music = false, musicIdle = false, artistPhotos = false, weather = false)
    }
}
