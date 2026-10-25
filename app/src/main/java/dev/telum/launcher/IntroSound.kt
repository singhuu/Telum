package dev.telum.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * The intro's hum: assets/intro/hum.ogg, or hum-calm.ogg with reduced motion, both rendered to the
 * picture's timeline by tools/make-intro-hum.py and levelled well below TV dialogue. It stays silent
 * when other audio is already playing, so it never talks over music. Main thread only.
 */
class IntroSound(private val context: Context, motion: Boolean) {
    private val asset = if (motion) "intro/hum.ogg" else "intro/hum-calm.ogg"
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    /** Starts the hum, with the intro's first frame. */
    fun start() {
        if (player != null || context.getSystemService(AudioManager::class.java).isMusicActive) return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                context.assets.openFd(asset).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("Telum", "intro sound: ${e.message}")
            null
        }
    }

    /** Fades out over [ms], with the picture, when the intro is skipped. */
    fun fadeOut(ms: Long) {
        val fading = player ?: return
        val began = SystemClock.uptimeMillis()
        main.post(object : Runnable {
            override fun run() {
                if (player !== fading) return
                val left = 1f - (SystemClock.uptimeMillis() - began).toFloat() / ms
                if (left <= 0f) { stop(); return }
                fading.setVolume(left * left, left * left)
                main.postDelayed(this, 16L)
            }
        })
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
