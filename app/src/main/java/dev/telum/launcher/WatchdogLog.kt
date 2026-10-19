package dev.telum.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The frame watchdog's findings, in logcat and in a small file that outlives logcat's ring
 * buffer, so a check days later still finds them. The file is in Telum's own media folder, the
 * one app folder that stock Android 11 lets adb read (it refuses Android/data), and it can be
 * read without Telum running:
 *
 *     adb shell cat /sdcard/Android/media/dev.telum.launcher/watchdog.log
 *
 * It holds names and counts only, never what is playing. Release builds are minified: retrace
 * stack frames with the R8 mapping from the matching release build.
 */
class WatchdogLog(context: Context) {
    private val app = context.applicationContext
    /** Used on the file thread only: SimpleDateFormat is not thread-safe. */
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun add(text: String, trace: Throwable? = null) {
        if (trace != null) Log.w("Telum", text, trace) else Log.w("Telum", text)
        val at = System.currentTimeMillis()
        io.execute {
            try {
                @Suppress("DEPRECATION") // still the only app folder adb can read on Android 11
                val dir = app.externalMediaDirs.firstOrNull()?.takeIf { it.isDirectory || it.mkdirs() } ?: app.filesDir
                File(dir, ".nomedia").takeIf { !it.exists() }?.createNewFile() // not for the media library
                val file = File(dir, "watchdog.log")
                // Kept small: past the limit, the older half goes.
                if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast(MAX_BYTES / 2).substringAfter('\n'))
                val frames = trace?.stackTrace?.take(10)?.joinToString("") { "\n\tat $it" }.orEmpty()
                file.appendText("${stamp.format(Date(at))} (build installed $installed) $text$frames\n")
            } catch (_: Exception) {
            }
        }
    }

    /** When this build was installed, to identify the installed version in reports. Read on the file thread. */
    private val installed by lazy {
        try {
            stamp.format(Date(app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime))
        } catch (_: Exception) {
            "unknown"
        }
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024

        /** One file thread for the process, started by the first line. */
        val io: ExecutorService by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "telum-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
        }
    }
}
