package dev.telum.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** Rejects oversized streams even when the server or provider does not declare a length. */
internal fun readArtworkBytes(input: InputStream, limit: Int = 6 * 1024 * 1024): ByteArray? {
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
}

/** One programme an app published for the TV home screen. */
class Programme(
    /** Stable for the same row: "<table>:<id>". */
    val key: String,
    val title: String,
    /** Episode line such as "S1 E3 · The long way home", or empty. */
    val detail: String,
    val position: Long,
    val duration: Long,
    val art: String?,
    val portrait: Boolean,
    val intent: String?,
) {
    val hasProgress: Boolean get() = position > 0 && duration > 0

    /** "24 min left", "1 hr 2 min left", or empty without progress. */
    val remaining: String
        get() {
            if (!hasProgress) return ""
            val min = ((duration - position).coerceAtLeast(0) + 59_999) / 60_000
            return when {
                min <= 1 -> "Almost done"
                min < 60 -> "$min min left"
                min % 60 == 0L -> "${min / 60} hr left"
                else -> "${min / 60} hr ${min % 60} min left"
            }
        }
}

/** A row of programmes from one app, under the app's own name for it. */
class Shelf(val pkg: String, val name: String, val items: List<Programme>) {
    /** True when the row carries viewing progress: real continue watching. */
    val progress: Boolean = items.any { it.hasProgress }
    val portrait: Boolean = items.count { it.portrait } * 2 > items.size
}

/**
 * Reads what other apps publish to the TV provider (Watch Next and their preview channels) and
 * loads the artwork. Needs the user's "read TV listings" permission. Queries and downloads run on background threads, and only while
 * Telum is in front. Nothing is written to the provider.
 */
class Content(private val context: Context, private val prefs: Prefs) {

    companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        /** Longest side of decoded artwork: covers the hero poster (163 units tall) at 1080p. */
        private const val ART_PX = 420
        private const val DISK_FILES = 80
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "telum-content").apply { priority = Thread.MIN_PRIORITY } }
    private val artIo = Executors.newFixedThreadPool(2) { r -> Thread(r, "telum-art").apply { priority = Thread.MIN_PRIORITY } }
    private val generation = AtomicLong()
    private val diskLock = Any()

    /**
     * Whether Android allows reading the rows. Asking Android is a call into another process, so
     * the answer is cached: it is read again on every start and after the permission prompt.
     * Android restarts the app if the user revokes the permission in Settings.
     */
    private var granted = checkPermission()

    private fun checkPermission() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun permitted(): Boolean = granted

    fun permissionChanged() { granted = checkPermission() }

    /** The feature is on and Android still allows it. Cheap: safe to call every frame. */
    val enabled: Boolean get() = prefs.watchingOn && granted

    /** Rows by package, in the provider's order. */
    @Volatile var shelves: Map<String, List<Shelf>> = emptyMap()
        private set

    private var querying = false
    private var queued = false
    private var visible = false
    private var onChange: (() -> Unit)? = null

    // ------------------------------------------------------------------ rows

    /** The row the hero shows for [pkg]: the user's choice, else a row with progress, else none. */
    fun shelfFor(pkg: String): Shelf? {
        if (!enabled) return null
        val list = shelves[pkg] ?: return null
        val pick = when (val choice = prefs.rowChoice(pkg)) {
            null -> list.firstOrNull { it.progress }
            "off" -> null
            else -> list.firstOrNull { it.name == choice } ?: list.firstOrNull { it.progress }
        }
        return pick?.takeIf { it.items.isNotEmpty() }
    }

    private var observing = false

    /** Starts watching the provider while Telum is in front. */
    fun start(changed: () -> Unit) {
        visible = true
        onChange = changed
        granted = checkPermission()
        if (!enabled) return
        if (!observing) {
            try {
                context.contentResolver.registerContentObserver(Uri.parse("content://${TvContract.AUTHORITY}"), true, observer)
                observing = true
            } catch (_: Exception) {}
        }
        refresh()
    }

    fun stop() {
        visible = false
        invalidateWork()
        onChange = null
        stopObserving()
    }

    private fun stopObserving() {
        main.removeCallbacks(debounced)
        if (observing) {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
            observing = false
        }
    }

    /** Turned off: forget everything read and every image. */
    fun clear() {
        invalidateWork()
        stopObserving()
        shelves = emptyMap()
        memory.evictAll()
        glows.evictAll()
        photos.evictAll()
        photoUrls.clear()
        failed.clear()
        artIo.execute { synchronized(diskLock) { File(context.cacheDir, "watching").deleteRecursively() } }
    }

    private fun invalidateWork() {
        generation.incrementAndGet()
        querying = false
        queued = false
        loading.clear()
        looking.clear()
    }

    private val debounced = Runnable { refresh() }

    // Apps update their rows in bursts (one insert per programme): refresh once it settles.
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(debounced)
            main.postDelayed(debounced, 1500L)
        }
    }

    fun refresh() {
        if (!enabled || !visible) return
        if (querying) { queued = true; return }
        querying = true
        val epoch = generation.get()
        io.execute {
            if (generation.get() != epoch) return@execute
            val read = try { read() } catch (_: Exception) { null }
            main.post {
                if (generation.get() != epoch || !visible || !enabled) return@post
                querying = false
                if (read != null) {
                    shelves = read
                    onChange?.invoke()
                }
                if (queued) { queued = false; refresh() }
            }
        }
    }

    /**
     * One read for setup, which offers each app's rows before Live Activities is switched on.
     * Needs only the permission. Loads no artwork and keeps nothing.
     */
    fun preview(result: (Map<String, List<Shelf>>) -> Unit) {
        granted = checkPermission()
        if (!granted) { result(emptyMap()); return }
        try {
            io.execute {
                val read = try { read() } catch (_: Exception) { null }
                main.post { result(read ?: emptyMap()) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {} // closed with the activity
    }

    /** Whole tables with no selection: the provider refuses a selection from callers without full access. */
    private fun read(): Map<String, List<Shelf>> {
        val own = context.packageName
        val out = LinkedHashMap<String, MutableList<Shelf>>()
        val now = System.currentTimeMillis()

        // Watch Next: continue and next-episode rows from the last month, most recent first.
        val next = ArrayList<Pair<Long, Pair<String, Programme>>>()
        query(TvContract.WatchNextPrograms.CONTENT_URI) { c ->
            val pkg = c.str("package_name") ?: return@query
            if (pkg == own) return@query
            val type = c.long("watch_next_type") ?: return@query
            val engaged = c.long("last_engagement_time_utc_millis") ?: 0L
            if ((type == 0L || type == 1L) && now - engaged < MONTH_MS) {
                programme(c, "wn:${c.long("_id")}")?.let { next += engaged to (pkg to it) }
            }
        }
        for ((pkg, items) in next.sortedByDescending { it.first }.map { it.second }.groupBy({ it.first }, { it.second })) {
            out.getOrPut(pkg) { ArrayList() } += Shelf(pkg, "Continue watching", items)
        }

        // Preview channels, by their own names.
        class Channel(val pkg: String, val name: String)
        val channels = LinkedHashMap<Long, Channel>()
        query(TvContract.Channels.CONTENT_URI) { c ->
            val pkg = c.str("package_name") ?: return@query
            if (pkg == own || c.str("type") != TvContract.Channels.TYPE_PREVIEW) return@query
            channels[c.long("_id") ?: return@query] = Channel(pkg, c.str("display_name") ?: "")
        }
        val byChannel = HashMap<Long, MutableList<Pair<Long, Programme>>>()
        query(TvContract.PreviewPrograms.CONTENT_URI) { c ->
            val id = c.long("channel_id") ?: return@query
            if (id !in channels || c.long("browsable") == 0L) return@query
            val p = programme(c, "pp:${c.long("_id")}") ?: return@query
            byChannel.getOrPut(id) { ArrayList() } += (c.long("weight") ?: 0L) to p
        }
        for ((id, ch) in channels) {
            val items = byChannel[id] ?: continue
            // Apps that weight their programmes want them in that order; otherwise keep the provider's.
            val ordered = if (items.any { it.first != 0L }) items.sortedByDescending { it.first } else items
            out.getOrPut(ch.pkg) { ArrayList() } += Shelf(ch.pkg, ch.name, ordered.map { it.second })
        }
        return out
    }

    private fun query(uri: Uri, row: (Cursor) -> Unit) {
        context.contentResolver.query(uri, null, null, null, null)?.use { c -> while (c.moveToNext()) row(c) }
    }

    private fun programme(c: Cursor, key: String): Programme? {
        val title = c.str("title")?.takeIf { it.isNotBlank() } ?: return null
        val season = c.str("season_display_number")
        val episode = c.str("episode_display_number")
        val number = listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" }).joinToString(" ")
        val detail = listOf(number, c.str("episode_title") ?: "").filter { it.isNotBlank() }.joinToString(" · ")
        val poster = c.str("poster_art_uri")
        val art = poster ?: c.str("thumbnail_uri")
        // 4 is 2:3 and 5 the movie-poster shape; Stremio sends posters.
        val aspect = if (poster != null) c.long("poster_art_aspect_ratio") else c.long("thumbnail_aspect_ratio")
        return Programme(
            key = key,
            title = title,
            detail = detail,
            position = c.long("last_playback_position_millis") ?: 0L,
            duration = c.long("duration_millis") ?: 0L,
            art = art,
            portrait = aspect == 4L || aspect == 5L,
            intent = c.str("intent_uri"),
        )
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.long(col: String): Long? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    // ------------------------------------------------------------------ artwork

    /** Decoded artwork, bounded by bytes (RGB_565 posters are about 0.2 MB each). */
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** The backdrop glow and colours made from each poster. */
    private val glows = LruCache<String, Pair<Bitmap, Mood>>(12)
    private val loading = HashSet<String>()
    /** Addresses that failed to load, and when. Drawing asks on every frame, so each waits a minute before another try. */
    private val failed = HashMap<String, Long>()

    private fun recentlyFailed(uri: String) = failed[uri]?.let { SystemClock.uptimeMillis() - it < 60_000L } == true

    /** The artwork if loaded; otherwise starts loading it (while Telum is in front) and returns null. */
    fun art(uri: String, forMusic: Boolean = false): Bitmap? {
        fun allowed() = if (forMusic) prefs.musicOn else enabled
        if (!allowed()) return null
        memory.get(uri)?.let { return it }
        if (!visible || recentlyFailed(uri) || !loading.add(uri)) return null
        val epoch = generation.get()
        artIo.execute {
            if (generation.get() != epoch) return@execute
            val bmp = try { load(uri, epoch = epoch, allowed = ::allowed) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            val glow = bmp?.let { try { Art.glow(it) to Palette.moodOf(it) } catch (_: Exception) { null } }
            main.post {
                if (generation.get() != epoch || !visible) return@post
                loading.remove(uri)
                if (!allowed()) return@post
                if (bmp != null) {
                    memory.put(uri, bmp)
                    glow?.let { glows.put(uri, it) }
                    onChange?.invoke()
                } else {
                    failed[uri] = SystemClock.uptimeMillis()
                }
            }
        }
        return null
    }

    fun glow(uri: String): Pair<Bitmap, Mood>? = glows.get(uri)

    // ------------------------------------------------------------------ artist photos (opt-in)

    /** Full-screen photos, at most 1920 x 1080 (about 4 MB as RGB_565), so one always fits. */
    private val photos = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Artist name to photo address, or "" when the lookup found none; kept while Telum runs. */
    private val photoUrls = HashMap<String, String>()
    private val looking = HashSet<String>()

    /**
     * The address of a wide (16:9) photo of [artist], if one is known; otherwise starts a lookup
     * on TheAudioDB, sending only the artist's name, and returns null. Used only when the user has
     * turned on artist photos. Each artist is looked up at most once per run.
     */
    fun artistPhotoUrl(artist: String): String? {
        if (!prefs.artistPhotos || !prefs.musicOn) return null
        photoUrls[artist]?.let { return it.ifEmpty { null } }
        if (!visible || !looking.add(artist)) return null
        val epoch = generation.get()
        artIo.execute {
            if (generation.get() != epoch) return@execute
            val url = if (prefs.artistPhotos && prefs.musicOn) {
                try { lookUpArtist(artist) } catch (_: Exception) { null }
            } else null
            main.post {
                if (generation.get() != epoch || !visible) return@post
                looking.remove(artist)
                if (!prefs.artistPhotos || !prefs.musicOn) return@post
                photoUrls[artist] = url ?: ""
                if (url != null) onChange?.invoke()
            }
        }
        return null
    }

    private fun lookUpArtist(artist: String): String? {
        // TheAudioDB's public key; its free tier allows 30 requests a minute.
        val q = URLEncoder.encode(artist, "UTF-8")
        val body = download("https://www.theaudiodb.com/api/v1/json/123/search.php?s=$q") ?: return null
        val a = JSONObject(String(body)).optJSONArray("artists")?.optJSONObject(0) ?: return null
        // Only a clear match: a photo of the wrong artist is worse than none.
        fun norm(s: String) = s.lowercase().removePrefix("the ").filter { it.isLetterOrDigit() }
        if (norm(a.optString("strArtist")) != norm(artist)) return null
        // "Fanart" is the database's 16:9 artist photo (usually 1280 x 720).
        return listOf("strArtistFanart", "strArtistFanart2", "strArtistFanart3", "strArtistFanart4")
            .map { a.optString(it, "") }.firstOrNull { it.startsWith("https://") }
    }

    /** A full-screen photo if loaded; otherwise starts loading it and returns null. */
    fun photo(url: String): Bitmap? {
        if (!prefs.artistPhotos || !prefs.musicOn) return null
        photos.get(url)?.let { return it }
        if (!visible || recentlyFailed(url) || !loading.add(url)) return null
        val epoch = generation.get()
        artIo.execute {
            if (generation.get() != epoch) return@execute
            val bmp = try {
                load(url, 1280, epoch) { prefs.artistPhotos && prefs.musicOn }?.let { fitScreen(it) }
            } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            main.post {
                if (generation.get() != epoch || !visible) return@post
                loading.remove(url)
                if (!prefs.artistPhotos || !prefs.musicOn) return@post
                if (bmp != null) {
                    photos.put(url, bmp)
                    onChange?.invoke()
                } else {
                    failed[url] = SystemClock.uptimeMillis()
                }
            }
        }
        return null
    }

    /**
     * Scaled down to fit 1920 x 1080. Decoding by halves leaves photos up to 2559 px wide, and one
     * over the cache's size would be dropped as soon as it was stored, then loaded again and again.
     */
    private fun fitScreen(b: Bitmap): Bitmap {
        val k = minOf(1920f / b.width, 1080f / b.height)
        if (k >= 1f) return b
        val s = Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
        if (s !== b) b.recycle()
        return s
    }

    private fun load(uri: String, maxPx: Int = ART_PX, epoch: Long, allowed: () -> Boolean = { true }): Bitmap? {
        if (generation.get() != epoch || !allowed()) return null
        val u = Uri.parse(uri)
        val bytes: ByteArray = when (u.scheme) {
            "https" -> fromDisk(uri) ?: download(uri)?.also { toDisk(uri, it, epoch, allowed) } ?: return null
            "content", "android.resource" -> context.contentResolver.openInputStream(u)?.use { readArtworkBytes(it) } ?: return null
            else -> return null // Android blocks plain http for this app.
        }
        if (generation.get() != epoch || !allowed()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun download(uri: String): ByteArray? {
        val c = URL(uri).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Telum-TV-Launcher")
        try {
            if (c.responseCode != 200) return null
            if (c.contentLengthLong > 6L * 1024 * 1024) return null
            return c.inputStream.use { readArtworkBytes(it) }
        } finally {
            c.disconnect()
        }
    }

    private fun diskFile(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "watching"), hash)
    }

    private fun fromDisk(uri: String): ByteArray? = synchronized(diskLock) {
        diskFile(uri).takeIf { it.exists() }?.let {
            it.setLastModified(System.currentTimeMillis())
            it.inputStream().use { input -> readArtworkBytes(input) }
        }
    }

    /** Keeps the newest [DISK_FILES] downloads (a few MB of posters). */
    private fun toDisk(uri: String, bytes: ByteArray, epoch: Long, allowed: () -> Boolean) = synchronized(diskLock) {
        if (generation.get() != epoch || !allowed()) return@synchronized
        val f = diskFile(uri)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        val files = f.parentFile?.listFiles() ?: return@synchronized
        if (files.size > DISK_FILES) files.sortedBy { it.lastModified() }.take(files.size - DISK_FILES).forEach { it.delete() }
    }

    fun close() {
        stop()
        io.shutdownNow()
        artIo.shutdownNow()
    }
}
