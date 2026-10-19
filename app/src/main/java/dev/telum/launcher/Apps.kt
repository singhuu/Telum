package dev.telum.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class AppEntry(
    /** Stable identity: "package/activity", or "telum:settings" for the built-in Settings tile. */
    val key: String,
    val pkg: String,
    val label: String,
    val component: ComponentName?,
    val isSystem: Boolean,
    val category: String,
) {
    /** Tile artwork at the focused tile's on-screen size; null until loaded off the main thread. */
    @Volatile var art: Bitmap? = null
    /** A tiny, blurred, colour-only copy of the artwork that lights the backdrop behind the hero. */
    @Volatile var glow: Bitmap? = null
    @Volatile var mood: Mood = Palette.appMood(pkg)

    val tagline: String get() = AppSupport.tagline(pkg, label)
}

/**
 * Finds the TV apps, loads their artwork on one background thread and keeps the order the user
 * chose. Nothing runs here unless the home screen asks for it.
 */
class Apps(private val context: Context, private val prefs: Prefs) {

    companion object {
        const val GLOW_W = 64
        const val GLOW_H = 36
        const val SETTINGS_KEY = "telum:settings"
        const val TV_SETTINGS = "com.android.tv.settings"

        /** The proposed favourites from the app review, in a starting order the user can change. */
        val DEFAULT_ORDER = listOf(
            "com.stremio.one",
            "com.plexapp.android",
            "org.smarttube.beta",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "ca.cbc.android.cbctv",
            "org.videolan.vlc",
            "com.esaba.downloader",
        )

        /** Apps that are useful but belong at the end of the grid. */
        val TAIL = listOf("com.android.vending", "com.plexapp.mediaserver.smb", TV_SETTINGS)

    }

    private val pm: PackageManager = context.packageManager

    /**
     * Artwork is decoded at the focused tile's real pixel size (156 x 1.17 layout units), so it
     * stays sharp whether Android composes the UI at 1080p or 4K, and is never larger than needed.
     */
    private val artW: Int = run {
        val dm = context.resources.displayMetrics
        val wide = maxOf(dm.widthPixels, dm.heightPixels)
        // A multiple of 16 keeps the 16:9 height exact.
        (wide / 960f * 156f * 1.17f).toInt().coerceIn(320, 768) / 16 * 16
    }
    private val artH: Int = artW * 9 / 16
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "telum-apps").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private val artCache = HashMap<String, Triple<Bitmap?, Bitmap?, Mood>>()
    @Volatile private var generation = 0
    @Volatile private var leanbackComponents: Set<ComponentName> = emptySet()

    /** All launchable apps, hidden ones included, in the user's order. */
    var all: List<AppEntry> = emptyList()
        private set

    fun visible(): List<AppEntry> {
        val hidden = prefs.hidden
        return all.filter { it.key !in hidden }
    }

    /**
     * Rebuilds the list. [onList] fires once with labels (artwork may still be missing), then
     * [onArt] fires as each tile's artwork arrives.
     */
    /** True from a load request until its list and artwork are done (or it is superseded). */
    @Volatile var loading = false
        private set

    fun load(onList: () -> Unit, onArt: (AppEntry) -> Unit) {
        val gen = ++generation
        loading = true
        worker.execute {
            // Any failure in here is logged and survived: an exception escaping an executor
            // task would kill the home screen's process.
            try {
                loadNow(gen, onList, onArt)
            } catch (t: Throwable) {
                Log.w("Telum", "app load failed", t)
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    private fun loadNow(gen: Int, onList: () -> Unit, onArt: (AppEntry) -> Unit) {
        val t0 = SystemClock.elapsedRealtime()
        val entries = query()
        val ordered = applyOrder(entries)
        // Reuse artwork already decoded for unchanged apps.
        for (e in ordered) artCache[e.key]?.let { (art, glow, mood) -> e.art = art; e.glow = glow; e.mood = mood }
        // Artwork saved on disk (the usual case after Android reclaimed Telum's memory) is read
        // before the list is shown, about 20 ms per app, so a restarted home screen appears whole
        // instead of flashing placeholders that then fade into artwork.
        var fromDisk = 0
        for (e in ordered) {
            if (gen != generation) return
            if (e.art != null) continue
            try {
                if (diskCache.read(e)) {
                    fromDisk++
                    artCache[e.key] = Triple(e.art, e.glow, e.mood)
                }
            } catch (t: Throwable) {
                Log.w("Telum", "cached artwork failed for ${e.pkg}", t)
            }
        }
        val t1 = SystemClock.elapsedRealtime()
        main.post {
            if (gen != generation) return@post
            all = ordered
            onList()
        }
        for (e in ordered) {
            if (gen != generation) return
            if (e.art != null) continue
            try {
                decorate(e)
                diskCache.write(e)
            } catch (t: Throwable) {
                // A broken resource in one app must never take the home screen down.
                Log.w("Telum", "artwork failed for ${e.pkg}", t)
            }
            // Resource failures still get an app-coloured tile and backdrop, without a network call.
            if (e.art == null) {
                e.art = Art.fallbackTile(e.label, artW, artH, e.mood)
                e.glow = Art.glow(e.art!!)
            }
            artCache[e.key] = Triple(e.art, e.glow, e.mood)
            main.post { if (gen == generation) onArt(e) }
        }
        if (gen == generation) diskCache.prune(ordered)
        Log.i("Telum", "apps: list and cached artwork ${t1 - t0} ms, new artwork ${SystemClock.elapsedRealtime() - t1} ms " +
            "(${ordered.size} apps, $fromDisk from cache)")
    }

    fun close() {
        worker.shutdownNow()
    }

    fun saveOrder(entries: List<AppEntry>) {
        // Keep hidden apps: write the full list, with the visible ones in their new order.
        val visibleKeys = entries.map { it.key }.toSet()
        val rest = all.filter { it.key !in visibleKeys }
        val newAll = entries + rest
        all = newAll
        prefs.order = newAll.map { it.key }
    }

    fun setHidden(key: String, hide: Boolean) {
        prefs.hidden = if (hide) prefs.hidden + key else prefs.hidden - key
    }

    /** Frees decoded artwork when Android asks for memory back. It is rebuilt on the next load. */
    fun trim() {
        generation++
        loading = false
        // The cache map belongs to the worker thread; clear it there.
        try { worker.execute { artCache.clear() } } catch (_: Exception) {}
        for (e in all) { e.art = null; e.glow = null }
    }

    fun launchIntent(e: AppEntry): Intent? {
        if (e.key == SETTINGS_KEY) return Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val c = e.component ?: return null
        return Intent(Intent.ACTION_MAIN)
            .setComponent(c)
            .addCategory(if (c in leanbackComponents) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private fun query(): List<AppEntry> {
        val self = context.packageName
        val out = LinkedHashMap<String, AppEntry>()
        val leanback = HashSet<ComponentName>()
        val lb = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        for (ri in lb) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == self || !ai.enabled || !ai.applicationInfo.enabled) continue
            val c = ComponentName(ai.packageName, ai.name)
            leanback.add(c)
            if (out.values.any { it.pkg == ai.packageName }) continue
            out[c.flattenToShortString()] = entryFor(ai, c)
        }
        // Phone-only apps the user installed (sideloads), but not the system's phone leftovers.
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        for (ri in phone) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == self || !ai.enabled || !ai.applicationInfo.enabled) continue
            val system = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (system || out.values.any { it.pkg == ai.packageName }) continue
            val c = ComponentName(ai.packageName, ai.name)
            out[c.flattenToShortString()] = entryFor(ai, c)
        }
        leanbackComponents = leanback
        if (out.values.none { it.pkg == TV_SETTINGS }) {
            out[SETTINGS_KEY] = AppEntry(SETTINGS_KEY, TV_SETTINGS, "Settings", null, true, "System")
        }
        return out.values.toList()
    }

    private fun entryFor(ai: ActivityInfo, c: ComponentName): AppEntry {
        val label = (ai.loadLabel(pm)?.toString() ?: ai.packageName).trim()
        val system = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val category = AppSupport.profile(ai.packageName)?.category ?: when (ai.applicationInfo.category) {
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_AUDIO -> "Music and audio"
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            else -> if (system) "System" else "App"
        }
        return AppEntry(c.flattenToShortString(), ai.packageName, label, c, system, category)
    }

    private fun rank(e: AppEntry): Int {
        val fav = DEFAULT_ORDER.indexOf(e.pkg)
        if (fav >= 0) return fav
        val tail = TAIL.indexOf(e.pkg)
        if (tail >= 0) return 10_000 + tail
        return if (e.isSystem) 5_000 else 1_000
    }

    private fun applyOrder(entries: List<AppEntry>): List<AppEntry> {
        val saved = prefs.order
        val savedSet = saved.toSet()
        val byKey = entries.associateBy { it.key }
        val known = saved.mapNotNull { byKey[it] }
        val fresh = entries.filter { it.key !in savedSet }
            .sortedWith(compareBy<AppEntry> { rank(it) }.thenBy { it.label.lowercase() })
        if (saved.isEmpty()) return fresh
        // New installs go just before the tail apps (Store, server, Settings) rather than after them.
        val result = known.toMutableList()
        for (f in fresh) {
            val firstTail = result.indexOfFirst { rank(it) >= 10_000 }
            if (rank(f) < 10_000 && firstTail >= 0) result.add(firstTail, f) else result.add(f)
        }
        return result
    }

    // ---------------------------------------------------------------- artwork

    /**
     * Finished tile artwork saved as raw pixels, so a return to Home after Android reclaimed
     * memory reads ~12 small files instead of opening every app's resources again. An entry is
     * reused only while the app's install time, the tile size and Telum's version are unchanged.
     */
    private val diskCache = object {
        private val dir = File(context.cacheDir, "art")
        private val selfUpdated = try { pm.getPackageInfo(context.packageName, 0).lastUpdateTime } catch (_: Exception) { 0L }

        private fun stamp(e: AppEntry): String {
            val updated = try { pm.getPackageInfo(e.pkg, 0).lastUpdateTime } catch (_: Exception) { 0L }
            // Telum's own install time too, so a new test build never shows old artwork.
            return "${BuildConfig.VERSION_CODE}-$selfUpdated-$artW-$updated"
        }

        private fun file(e: AppEntry) = File(dir, Integer.toHexString(e.key.hashCode()))

        /** Deletes cached artwork for apps that are gone. */
        fun prune(entries: List<AppEntry>) {
            val keep = entries.map { file(it).name }.toSet()
            dir.listFiles()?.forEach { if (it.name.substringBefore('.') !in keep) it.delete() }
        }

        fun read(e: AppEntry): Boolean {
            val f = file(e)
            val meta = File(f.path + ".txt")
            if (!meta.exists()) return false
            val lines = meta.readText().split('\n')
            if (lines.size < 2 || lines[0] != stamp(e)) return false
            val colors = lines[1].split(',').mapNotNull { it.trim().toLongOrNull()?.toInt() }
            if (colors.size != 4) return false
            val art = readRaw(File(f.path + ".art"), artW, artH) ?: return false
            e.art = art
            e.mood = Mood(colors[0], colors[1], colors[2], colors[3])
            e.glow = readRaw(File(f.path + ".glow"), GLOW_W, GLOW_H) ?: Art.glow(art)
            return true
        }

        fun write(e: AppEntry) {
            val art = e.art ?: return
            dir.mkdirs()
            val f = file(e)
            writeRaw(File(f.path + ".art"), art)
            e.glow?.let { writeRaw(File(f.path + ".glow"), it) }
            val m = e.mood
            File(f.path + ".txt").writeText(stamp(e) + "\n" + listOf(m.base, m.a, m.b, m.c).joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })
        }

        // Raw pixels: reading 360 KB is far quicker than decoding a PNG on the SHIELD's CPU.
        private fun readRaw(f: File, w: Int, h: Int): Bitmap? {
            if (f.length() != w * h * 4L) return null
            val buf = ByteBuffer.wrap(f.readBytes())
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buf) }
        }

        private fun writeRaw(f: File, b: Bitmap) {
            val buf = ByteBuffer.allocate(b.byteCount)
            b.copyPixelsToBuffer(buf)
            f.writeBytes(buf.array())
        }
    }

    private fun decorate(e: AppEntry) {
        if (e.pkg == TV_SETTINGS) {
            // The stock Settings banner is a 2014 long-shadow design; draw a cleaner one.
            e.art = Art.settingsTile(artW, artH)
            e.mood = Mood(0xFF07090D.toInt(), 0xFF56637C.toInt(), 0xFF2E3D5C.toInt(), 0xFF6A7690.toInt())
            e.glow = Art.glow(e.art!!)
            return
        }
        val c = e.component ?: return
        val ai = pm.getActivityInfo(c, 0)
        val res = pm.getResourcesForApplication(ai.applicationInfo)
        val bannerId = if (ai.banner != 0) ai.banner else ai.applicationInfo.banner
        val banner: Drawable? = if (bannerId != 0) {
            try { res.getDrawableForDensity(bannerId, DisplayMetrics.DENSITY_XXHIGH, null) } catch (_: Exception) { null }
        } else null
        val art = Bitmap.createBitmap(artW, artH, Bitmap.Config.ARGB_8888)
        if (banner != null) {
            Art.drawCover(Canvas(art), banner, artW, artH)
            e.mood = Palette.appMood(e.pkg, art)
        } else {
            val iconId = ai.iconResource
            val iconDrawable: Drawable = (if (iconId != 0) {
                try { res.getDrawableForDensity(iconId, DisplayMetrics.DENSITY_XXXHIGH, null) } catch (_: Exception) { null }
            } else null) ?: ai.loadIcon(pm)
            val icon = Art.roundedIcon(iconDrawable, 160)
            e.mood = Palette.appMood(e.pkg, icon)
            Art.composedTile(Canvas(art), icon, e.label, e.mood, artW, artH)
            icon.recycle()
        }
        e.art = art
        e.glow = Art.glow(art)
    }
}

/** Off-screen drawing for tile artwork. */
object Art {
    fun drawCover(cv: Canvas, d: Drawable, w: Int, h: Int) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = max(w.toFloat() / iw, h.toFloat() / ih)
        val dw = (iw * scale).toInt()
        val dh = (ih * scale).toInt()
        val l = (w - dw) / 2
        val t = (h - dh) / 2
        d.setBounds(l, t, l + dw, t + dh)
        d.isFilterBitmap = true
        d.draw(cv)
    }

    fun roundedIcon(d: Drawable, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, Path.Direction.CW) }
        cv.clipPath(clip)
        if (d is AdaptiveIconDrawable) {
            // Adaptive layers are 108 units with the 72-unit safe zone in the middle.
            val pad = (size * 18f / 72f).toInt()
            d.background?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(cv) }
            d.foreground?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(cv) }
        } else {
            cv.drawColor(0xFF1C1F27.toInt())
            val inset = (size * 0.08f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(cv)
        }
        return out
    }

    /** For apps without a TV banner: the icon on a gradient from its own colours, with the name. */
    fun composedTile(cv: Canvas, icon: Bitmap, label: String, mood: Mood, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), mood.a, lerpColor(mood.c, Color.BLACK, 0.45f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val s = h * 0.46f
        val l = w * 0.1f
        val t = (h - s) / 2f
        p.setShadowLayer(h * 0.05f, 0f, h * 0.015f, 0x55000000)
        cv.drawBitmap(icon, null, RectF(l, t, l + s, t + s), p)
        p.clearShadowLayer()
        p.color = Color.WHITE
        p.textSize = h * 0.15f
        p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 600, false)
        var text = label
        val maxW = w - (l + s + w * 0.06f) - w * 0.06f
        while (p.measureText(text) > maxW && text.length > 4) text = text.dropLast(2) + "…"
        cv.drawText(text, l + s + w * 0.06f, h / 2f + p.textSize * 0.35f, p)
    }

    /** Last resort when an app's artwork cannot be read: its name on the app's local palette. */
    fun fallbackTile(label: String, w: Int, h: Int, mood: Mood = Mood.DEFAULT): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), mood.a, mood.base, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.WHITE
        p.textSize = h * 0.16f
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 600, false)
        var text = label
        while (p.measureText(text) > w * 0.86f && text.length > 4) text = text.dropLast(2) + "…"
        cv.drawText(text, w / 2f, h / 2f + p.textSize * 0.35f, p)
        return out
    }

    fun settingsTile(w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF9AA2B2.toInt(), 0xFF555D6E.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.5f, h * 0.15f, w * 0.6f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        Glyphs.gear(cv, w / 2f, h / 2f, h * 0.56f, 0xFFF5F6F9.toInt(), 0xFF6A7284.toInt())
        return out
    }

    /**
     * Downscales and blurs the artwork, keeping only its coloured parts: white text and black
     * backgrounds fade to transparent, so the backdrop glows in the logo's own colours.
     */
    fun glow(art: Bitmap): Bitmap {
        val w = Apps.GLOW_W
        val h = Apps.GLOW_H
        val small = Bitmap.createScaledBitmap(art, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        val hsv = FloatArray(3)
        val alpha = FloatArray(px.size)
        for (i in px.indices) {
            Color.colorToHSV(px[i], hsv)
            alpha[i] = min(1f, max(0f, (hsv[1] - 0.12f) * 1.6f)) * min(1f, hsv[2] * 2.2f)
        }
        // Artwork that is colour edge to edge (a red or blue banner) would flood the screen; cap
        // the total light so every app glows with similar strength.
        val coverage = alpha.average().toFloat()
        val gain = if (coverage > 0.22f) 0.22f / coverage else 1f
        for (i in px.indices) {
            Color.colorToHSV(px[i], hsv)
            // Elliptical falloff so the glow has no edges.
            val x = (i % w + 0.5f) / w * 2f - 1f
            val y = (i / w + 0.5f) / h * 2f - 1f
            val d = Math.sqrt((x * x + y * y).toDouble()).toFloat()
            val edge = 1f - smooth(0.45f, 1f, d)
            hsv[1] = min(1f, hsv[1] * 1.15f)
            hsv[2] = 0.55f + 0.3f * hsv[2]
            px[i] = Color.HSVToColor((alpha[i] * gain * edge * 255).toInt().coerceIn(0, 255), hsv)
        }
        repeat(3) { boxBlur(px, w, h, 3) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** The middle of [art] in the [w]:[h] shape, for centre-cropping. */
    private fun crop(art: Bitmap, w: Int, h: Int): Rect {
        val want = w.toFloat() / h
        val have = art.width.toFloat() / art.height
        return if (have > want) {
            val cw = (art.height * want).toInt()
            Rect((art.width - cw) / 2, 0, (art.width + cw) / 2, art.height)
        } else {
            val ch = (art.width / want).toInt()
            Rect(0, (art.height - ch) / 2, art.width, (art.height + ch) / 2)
        }
    }

    /**
     * Album art as a backdrop: centre-cropped to 16:9, shrunk to 96 x 54 and blurred. Drawn
     * full-screen it becomes a soft field of the artwork's own colours, at almost no cost.
     */
    fun wash(art: Bitmap): Bitmap {
        val w = 96
        val h = 54
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(art, crop(art, w, h), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 4) }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Album art for the hero, large: square, [size] px, with soft edges baked in so it melts into
     * the backdrop. It fades out toward the title on the left and toward the apps below, and dims
     * a little at the top where the clock sits. Baked once per track, then drawn as a plain image.
     */
    fun faded(art: Bitmap, size: Int = 360): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val s = size.toFloat()
        cv.drawBitmap(art, crop(art, 1, 1), Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        p.shader = LinearGradient(0f, 0f, s * 0.4f, 0f, intArrayOf(0x00000000, 0x50000000, 0xFF000000.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s, p)
        p.shader = LinearGradient(0f, s * 0.58f, 0f, s, intArrayOf(0xFF000000.toInt(), 0x60000000, 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s, p)
        p.shader = LinearGradient(0f, 0f, 0f, s * 0.22f, 0x70000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, s, s, p)
        return out
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Separable box blur on premultiplied-ish ARGB (alpha-weighted so edges don't darken). */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var a = 0f; var rr = 0f; var g = 0f; var b = 0f; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (horizontal) src[line * w + j] else src[j * w + line]
                        val ca = Color.alpha(c) / 255f
                        a += ca; rr += Color.red(c) * ca; g += Color.green(c) * ca; b += Color.blue(c) * ca; n++
                    }
                    val outA = a / n
                    val c = if (a > 0f) Color.argb((outA * 255).toInt(), (rr / a).toInt(), (g / a).toInt(), (b / a).toInt()) else 0
                    if (horizontal) dst[line * w + i] = c else dst[i * w + line] = c
                }
            }
        }
        pass(px, tmp, true)
        pass(tmp, px, false)
    }
}
