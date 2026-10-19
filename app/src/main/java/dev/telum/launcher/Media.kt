package dev.telum.launcher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.max

/**
 * What the hero builds from a track's artwork, once per track: the blurred full-screen backdrop,
 * the large soft-edged artwork, and the glow and colours.
 */
class TrackVisuals(val wash: Bitmap, val faded: Bitmap, val glow: Bitmap, val mood: Mood) {
    companion object {
        fun of(art: Bitmap): TrackVisuals? = try {
            TrackVisuals(Art.wash(art), Art.faded(art), Art.glow(art), Palette.moodOf(art))
        } catch (_: Exception) {
            null
        }
    }
}

/** What another app is playing, as its media session describes it. */
class NowPlaying(
    val pkg: String,
    val title: String,
    /** "Artist · Album", or empty. */
    val subtitle: String,
    /** The first artist alone, for looking up an artist photo; or empty. */
    val artist: String,
    val playing: Boolean,
    private val position: Long,
    val duration: Long,
    private val updatedAt: Long,
    private val speed: Float,
    /** The session's own artwork, scaled down; or null with [artUri] to load instead. */
    val art: Bitmap?,
    val artUri: String?,
    val canNext: Boolean,
    val canPrevious: Boolean,
) {
    /** Same track from the same app (play and pause keep the key). */
    val key: String get() = "$pkg|$title|$subtitle"

    fun positionNow(): Long {
        if (!playing || updatedAt <= 0) return position
        return (position + (SystemClock.elapsedRealtime() - updatedAt) * speed).toLong().coerceIn(0, max(duration, position))
    }

    /** Whether [other] says exactly the same, as when an app repeats a callback without changing anything. */
    fun sameAs(other: NowPlaying?): Boolean = other != null && pkg == other.pkg && title == other.title &&
        subtitle == other.subtitle && artist == other.artist && playing == other.playing && position == other.position &&
        duration == other.duration && updatedAt == other.updatedAt && speed == other.speed && art === other.art &&
        artUri == other.artUri && canNext == other.canNext && canPrevious == other.canPrevious
}

/**
 * Follows other apps' media sessions while Home is in front, and sends them play, pause and skip.
 * Android allows this only once the user grants Telum notification access (Settings › Apps ›
 * Special app access › Notification access); see [MediaListener]. Nothing runs while Telum is
 * hidden: callbacks are registered in [start] and removed in [stop].
 */
class Media(private val context: Context, private val prefs: Prefs) {

    private val component = ComponentName(context, MediaListener::class.java)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var granted = check()
    private var listening = false
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var onChange: (() -> Unit)? = null

    /** The track to show: one that is playing, otherwise a paused one that can still be resumed. */
    var now: NowPlaying? = null
        private set

    /** Scaled-down session artwork, and what the hero makes from it, for the current track only. */
    private var artFor: String? = null
    private var artGeneration: Int? = null
    private var artSmall: Bitmap? = null
    private var visuals: TrackVisuals? = null
    /** The previous track's visuals too, so its fade-out keeps its own artwork. */
    private val recent = LinkedHashMap<String, TrackVisuals>()

    fun visualsFor(key: String): TrackVisuals? = recent[key]

    private fun check(): Boolean = try {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    } catch (_: Exception) {
        false
    }

    /** Notification access is granted (cached; read again on every start). */
    fun granted(): Boolean = granted

    val enabled: Boolean get() = granted && prefs.musicOn

    fun start(changed: () -> Unit) {
        onChange = changed
        granted = check()
        if (!enabled || listening) { if (!enabled) clear(); return }
        try {
            sessions.addOnActiveSessionsChangedListener(listener, component, main)
            listening = true
            setControllers(sessions.getActiveSessions(component))
        } catch (_: SecurityException) {
            granted = false
            clear()
        }
    }

    fun stop() {
        if (listening) {
            try { sessions.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) {}
            listening = false
        }
        for (c in controllers) c.unregisterCallback(callback)
        controllers = emptyList()
    }

    /** Turned off: stop following and forget the track. */
    fun clear() {
        stop()
        active = null
        now = null
        artFor = null; artGeneration = null; artSmall = null; visuals = null; recent.clear()
        onChange?.invoke()
    }

    fun playPause() {
        val c = active ?: return
        if (now?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { active?.transportControls?.skipToNext() }

    fun previous() { active?.transportControls?.skipToPrevious() }

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        if (listening && enabled) setControllers(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { update() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { update(metadataChanged = true) }
        override fun onSessionDestroyed() { update() }
    }

    private fun setControllers(list: List<MediaController>) {
        for (c in controllers) c.unregisterCallback(callback)
        controllers = list.filter { it.packageName != context.packageName }
        for (c in controllers) c.registerCallback(callback, main)
        update(metadataChanged = true)
    }

    private fun update(metadataChanged: Boolean = false) {
        if (!listening || !enabled) return
        // Sessions come most important first. A playing one wins; otherwise a paused one with a
        // track, which the controls can resume. Stopped or empty sessions are ignored.
        fun state(c: MediaController) = c.playbackState?.state ?: PlaybackState.STATE_NONE
        val c = controllers.firstOrNull { state(it) == PlaybackState.STATE_PLAYING || state(it) == PlaybackState.STATE_BUFFERING }
            ?: controllers.firstOrNull { state(it) == PlaybackState.STATE_PAUSED && it.metadata != null }
        val changedSession = active?.sessionToken != c?.sessionToken
        active = c
        val next = c?.let { build(it, metadataChanged || changedSession) }
        // Home redraws only when something it shows has changed.
        if (if (next == null) now == null else next.sameAs(now)) return
        now = next
        onChange?.invoke()
    }

    private fun build(c: MediaController, refreshArtwork: Boolean): NowPlaying? {
        val m = c.metadata ?: return null
        val title = (m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.takeIf { it.isNotBlank() } ?: return null
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val subtitle = listOfNotNull(artist, album).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val s = c.playbackState
        val state = s?.state ?: PlaybackState.STATE_NONE
        val actions = s?.actions ?: 0L
        val key = "${c.packageName}|$title|$subtitle"
        val changedTrack = key != artFor
        val bmp = if (changedTrack || refreshArtwork) {
            m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } else null
        if (changedTrack || (refreshArtwork && bmp?.generationId != artGeneration)) {
            artFor = key
            artGeneration = bmp?.generationId
            artSmall = bmp?.let { scaled(it) }
            visuals = artSmall?.let { TrackVisuals.of(it) }
            recent.remove(key)
            visuals?.let { recent[key] = it }
            while (recent.size > 2) recent.remove(recent.keys.first())
        }
        return NowPlaying(
            pkg = c.packageName,
            title = title,
            subtitle = subtitle,
            artist = artist?.split(',', '&', ';')?.firstOrNull()?.trim().orEmpty(),
            playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
            position = s?.position ?: 0L,
            duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            updatedAt = s?.lastPositionUpdateTime ?: 0L,
            speed = s?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            art = artSmall,
            artUri = if (artSmall != null) null else (m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: m.getString(MediaMetadata.METADATA_KEY_ART_URI) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)),
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }

    /** At most 400 px a side: enough for the large hero artwork, and the session's bitmap is not kept. */
    private fun scaled(b: Bitmap): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= 400) return b.copy(Bitmap.Config.ARGB_8888, false) ?: b
        val k = 400f / longest
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }
}
