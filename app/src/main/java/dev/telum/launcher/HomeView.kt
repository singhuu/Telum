package dev.telum.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The whole home screen in one hardware-accelerated view: ambient backdrop, hero, app grid, top
 * bar, menus and the side sheet. Drawing it ourselves avoids view-tree layout work on every focus
 * move and keeps the process small. Everything animates with [Spring]s and stops redrawing when
 * idle or hidden.
 */
@SuppressLint("ViewConstructor")
class HomeView(context: Context, private val host: Host, private val prefs: Prefs) : View(context) {

    interface Host {
        fun launch(entry: AppEntry, bounds: Rect)
        fun appInfo(entry: AppEntry)
        fun system(action: String)
        fun saveOrder(entries: List<AppEntry>)
        fun hide(entry: AppEntry)
        fun unhide(key: String)
        fun allApps(): List<AppEntry>
        fun editPlace()
        fun settingsChanged(what: String)
        fun weather(): WeatherNow?
        /** Starts a background memory reading and returns the latest. */
        fun memoryLine(): String
        /** The latest memory reading, without starting a new one (cheap enough to call per frame). */
        fun memoryText(): String
        fun stockHomeEnabled(): Boolean
        fun reloadApps()
        /** Rows other apps publish (continue watching); see Content. */
        fun content(): Content
        fun openProgramme(pkg: String, programme: Programme)
        /** Turns "Show what you're watching" on (asking Android for permission) or off. */
        fun setWatching(on: Boolean)
        /** What other apps are playing; see Media. */
        fun media(): Media
        /** Turns music controls on (sending the user to grant notification access first) or off. */
        fun setMusicControls(on: Boolean)
    }

    // ------------------------------------------------------------------ layout (960 x 540 units)

    private companion object {
        const val W = 960f
        const val MARGIN = 48f
        const val COLS = 5
        const val TILE_W = 156f
        const val TILE_H = 87.75f
        const val GAP = 21f
        /** First row at 54% of the height, so the second row sits fully inside the safe area. */
        const val ROW0 = 290f
        const val PITCH = 128f
        const val FOCUS_SCALE = 0.10f
        /** Baseline of the hero title. */
        const val HERO_BASE = 202f
        const val RADIUS = 11f
        const val BROWSE_ROW_Y = 200f
        const val LONG_PRESS_MS = 480L
        /** How long focus must rest before the backdrop follows it, so held arrows only move tiles. */
        const val DWELL_MS = 180L
        const val LIGHT_SCALE = 4f
    }

    private var u = 2f
    private fun px(v: Float) = v * u

    // ------------------------------------------------------------------ model

    private inner class Tile(var app: AppEntry) {
        val x = Spring(0f, 300f, 0.86f)
        val y = Spring(0f, 300f, 0.86f)
        val focus = Spring(0f, 300f, 0.9f)
        val appear = Spring(0f, 170f, 0.9f)
        /** Artwork fades in over its placeholder when it arrives after the tile was drawn. */
        val artFade = Spring(1f, 260f, 1f)
        var placeholderShown = false
        var appearAt = 0L
        var shader: BitmapShader? = null
        var shaderFor: Bitmap? = null
        val jigglePhase = Random().nextFloat() * 6.28f
    }

    private var tiles: List<Tile> = emptyList()
    private var focusIndex = 0

    /**
     * GRID: the apps. TOP: search and Control center (and the now-playing chip). QUEUE: the focused
     * app's row of programmes. MEDIA: play, pause and skip for the app that is playing.
     */
    private enum class Zone { GRID, TOP, QUEUE, MEDIA }
    private var zone = Zone.GRID
        set(v) {
            if (field == v) return
            field = v
            // The top bar shows the greeting, lit by the time of day instead of the focused app.
            // The queue keeps the backdrop of the programme it opened on.
            when (v) {
                Zone.TOP -> moodTowards(homeMood(), instant = false, app = null)
                Zone.GRID -> tiles.getOrNull(focusIndex)?.app?.let { focusChanged(it) }
                Zone.QUEUE, Zone.MEDIA -> {}
            }
        }

    private fun homeMood(): Mood =
        if (prefs.wallpaper == "dynamic") Palette.timeOfDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        else Palette.theme(prefs.wallpaper)
    private var topIndex = 0
    private val topButtons = listOf("search", "controls")
    private val topFocus = listOf(Spring(0f, 420f, 0.7f), Spring(0f, 420f, 0.7f))

    private var moving = false
    private val lift = Spring(0f, 300f, 0.7f)

    private val scroll = Spring(0f, 200f, 1f)
    /** 1 while the hero shows (first row or top bar), 0 while browsing lower rows. */
    private val hero = Spring(1f, 140f, 1f)

    private var moodFrom = Mood.DEFAULT
    private var moodTo = Mood.DEFAULT
    private val moodT = Spring(1f, 40f, 1f)

    /**
     * A blurred glow behind the hero: the focused app's own colours, or the poster of the
     * programme the hero shows. Each layer fades from whatever opacity it has, so an interrupted
     * change never drops a half-visible glow.
     */
    private inner class Glow(val key: String, var app: AppEntry?, val art: String?, val fixed: Bitmap? = null) {
        val alpha = Spring(0f, 40f, 1f)
        fun bitmap(): Bitmap? = fixed ?: if (art != null) host.content().glow(art)?.first else app?.glow
    }
    private val glows = ArrayList<Glow>()
    private var glowKey: String? = null

    /** A backdrop change waiting for focus to rest ([DWELL_MS]). */
    private var wantMood: Mood? = null
    private var wantGlow: AppEntry? = null
    private var wantSince = 0L

    /**
     * What the hero shows: the ambient greeting (no shelf), or one programme from an app's row.
     * Layers cross-fade from their current opacity, like the glows.
     */
    private inner class HeroLayer(val app: AppEntry?, var shelf: Shelf?, val index: Int, var media: NowPlaying? = null) {
        val alpha = Spring(0f, 90f, 1f)
        val rise = Spring(0f, 140f, 1f)
        val programme: Programme? get() = shelf?.items?.getOrNull(index)
        val ambient: Boolean get() = shelf == null && media == null
        /** Strings built once, not per frame. */
        var context = ""
        var count = ""
        var remaining = ""
        /** Backdrop for a programme or track: 0 not applied yet, 1 the app's colours, 2 the artwork's. */
        var backdrop = 0
        fun texts() {
            media?.let { m ->
                context = "${app?.label ?: ""} · ${if (m.playing) "Now playing" else "Paused"}"
                return
            }
            val s = shelf ?: return
            context = "${app?.label ?: ""} · ${s.name}"
            count = if (s.items.size == 1) "1 title" else "${s.items.size} titles"
            remaining = programme?.remaining ?: ""
        }
    }
    private val heroLayers = ArrayList<HeroLayer>()
    private var heroShown: HeroLayer? = null
    private var wantHeroSig: String? = null
    private var wantHeroSince = 0L

    // The queue: Up from a first-row app with a row of programmes.
    private var queueApp: AppEntry? = null
    private var queueShelf: Shelf? = null
    private var queueIndex = 0
    private val queueOpen = Spring(0f, 220f, 1f)
    private val queueScroll = Spring(0f, 260f, 1f)
    private var cardFocus: Array<Spring> = emptyArray()

    // Music controls: Up from the app that is playing opens them under its track.
    private var mediaApp: AppEntry? = null
    /** 0 previous, 1 play or pause, 2 next. */
    private var mediaIndex = 1
    private val mediaOpen = Spring(0f, 220f, 1f)
    private val mediaFocus = Array(3) { Spring(0f, 420f, 0.8f) }
    // The now-playing chip in the top bar, shown wherever the hero is not already showing the track.
    private var chipFocused = false
    private val chipShow = Spring(0f, 160f, 1f)
    private val chipFocus = Spring(0f, 420f, 0.8f)

    private val musicIdle = MusicIdlePolicy()
    private val musicIdleOpen = Spring(0f, 90f, 1f)
    private var active = false
    private var idleShift = 0
    private var idleWakeKey: Int? = null
    private var idleWakeTouch = false
    private val idleTitleP = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val idleSubtitleP = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val pressS = Spring(0f, 700f, 0.8f)
    private val nudgeX = Spring(0f, 400f, 0.7f)
    private val nudgeY = Spring(0f, 400f, 0.7f)

    // Menu (long press) and the side sheet.
    private var menuItems: List<SheetItem> = emptyList()
    private var menuFocus = 0
    private var menuFor: Tile? = null
    private val menuOpen = Spring(0f, 520f, 0.82f)
    private val menuPill = Spring(0f, 600f, 0.85f)

    private val pages = ArrayList<SheetPage>()
    private val sheetOpen = Spring(0f, 380f, 0.9f)
    private val sheetPill = Spring(0f, 600f, 0.85f)
    private val pageSwap = Spring(1f, 300f, 0.95f)
    private var pageDir = 1

    private var toastText: String? = null
    private var toastUntil = 0L
    private val toastS = Spring(0f, 300f, 0.9f)

    private var lastFrame = 0L
    /** Frame watchdog: if frames keep coming for 5 s with no key pressed, one line (logcat and a file) names why. */
    private val watchdog = WatchdogLog(context)
    private var prevDraw = 0L
    private var awakeSince = 0L
    private var lastKeyAt = 0L
    private var awakeLogged = false
    /** Wake-ups while awake: all kicks, then media, content, art and app-list changes. */
    private val wakes = IntArray(5)
    /** After the watchdog trips, the next few kicks log where they came from. */
    private var traceKicks = 0
    /** Time for the rearrange wobble; it only advances while an app is being moved. */
    private var t = 0f
    private var longPressFired = false
    private var okDown = false
    private var firstLoad = true

    // ------------------------------------------------------------------ paints

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blobPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // Typefaces are set by applyTypeface(). Hierarchy comes from size, weight and opacity, never capitals.
    private val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; letterSpacing = -0.02f }
    private val tagP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
    private val ctxP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val metaP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CFFFFFF.toInt() }
    private val labelP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val timeP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.RIGHT; fontFeatureSettings = "'tnum'" }
    private val itemP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val valueP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textAlign = Paint.Align.RIGHT }
    private val bigTimeP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; letterSpacing = -0.03f; fontFeatureSettings = "'tnum'" }

    private var blob: Bitmap? = null
    private var blobShader: BitmapShader? = null
    private var noiseShader: BitmapShader? = null
    private var shadowBmp: Bitmap? = null
    private var shadowPad = 0f
    private val m = Matrix()
    private val rf = RectF()
    private val rf2 = RectF()
    private val rf3 = RectF()
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** One shader per programme image, kept for the few on screen (bounded, so evicted bitmaps are freed). */
    private val artShaders = object : LinkedHashMap<String, Pair<Bitmap, BitmapShader>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Bitmap, BitmapShader>>) = size > 24
    }
    private var sheetWash: RadialGradient? = null
    private var sheetWashColor = 0
    private var glareShader: LinearGradient? = null
    private var edgeShader: LinearGradient? = null
    private var bottomScrim: LinearGradient? = null
    private var topScrim: LinearGradient? = null
    private var leftScrim: RadialGradient? = null
    private var photoLeft: LinearGradient? = null
    private var photoBottom: LinearGradient? = null
    private var photoTop: LinearGradient? = null

    private val timeFmt12 = SimpleDateFormat("h:mm", Locale.getDefault())
    private val timeFmt24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(false)
        applyTypeface()
        applyMotion()
    }

    /** Points every paint at the chosen family: 400 body, 500 labels, 600 titles, 300 for the large clock. */
    fun applyTypeface() {
        val a = context.assets
        val id = prefs.typeface
        titleP.typeface = Fonts.face(a, id, 600, display = true)
        bigTimeP.typeface = Fonts.face(a, id, 300, display = true)
        val label = Fonts.face(a, id, 500)
        for (p in arrayOf(tagP, ctxP, metaP, labelP, timeP, itemP, valueP)) p.typeface = label
        kick()
    }

    /**
     * Reduced motion keeps immediate feedback but drops the slow fades and the rearrange wobble:
     * everything settles in well under 200 ms.
     */
    fun applyMotion() {
        val full = prefs.motion
        for (tile in tiles) tile.focus.tune(if (full) 300f else 900f, if (full) 0.9f else 1f)
        moodT.tune(if (full) 40f else 400f, 1f)
        for (g in glows) g.alpha.tune(if (full) 40f else 400f, 1f)
        hero.tune(if (full) 140f else 600f, 1f)
        scroll.tune(if (full) 200f else 700f, 1f)
        queueOpen.tune(if (full) 220f else 700f, 1f)
        queueScroll.tune(if (full) 260f else 700f, 1f)
        for (s in cardFocus) s.tune(if (full) 300f else 900f, if (full) 0.9f else 1f)
        mediaOpen.tune(if (full) 220f else 700f, 1f)
        for (s in mediaFocus) s.tune(if (full) 420f else 900f, if (full) 0.8f else 1f)
        chipShow.tune(if (full) 160f else 700f, 1f)
        musicIdleOpen.tune(if (full) 90f else 700f, 1f)
        kick()
    }

    /** Media sessions changed (a track started, paused or ended) or music controls were switched. */
    fun mediaChanged() {
        wakes[1]++
        if (zone == Zone.MEDIA && host.media().now == null) closeMedia()
        updateMusicIdle()
        kick()
    }

    private fun nowPlaying(): NowPlaying? = if (host.media().enabled) host.media().now else null

    private fun canDrawFrames() = active && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus()

    private fun musicIdleEligible() = canDrawFrames() && nowPlaying()?.let { it.playing && AppSupport.isMusic(it.pkg) } == true &&
        !moving && !okDown && !menuOpen() && !sheetOpen()

    private val musicIdleCheck = Runnable { updateMusicIdle(); kick() }
    private val musicIdleShift = object : Runnable {
        override fun run() {
            if (!musicIdle.dimmed || !musicIdleEligible()) { updateMusicIdle(); return }
            idleShift = (idleShift + 1) % 8
            kick()
            postDelayed(this, 60_000L)
        }
    }
    private val mediaProgressTick = Runnable { if (canDrawFrames()) kick() }

    /** One deadline while browsing and one small position change per minute while dimmed. */
    private fun updateMusicIdle() {
        val now = SystemClock.uptimeMillis()
        val wasDimmed = musicIdle.dimmed
        musicIdle.update(now, musicIdleEligible())
        musicIdleOpen.target = if (musicIdle.dimmed) 1f else 0f
        removeCallbacks(musicIdleCheck)
        musicIdle.delay(now)?.let { postDelayed(musicIdleCheck, it) }
        if (musicIdle.dimmed && !wasDimmed) {
            idleShift = 0
            removeCallbacks(mediaProgressTick)
            postDelayed(musicIdleShift, 60_000L)
        } else if (!musicIdle.dimmed) removeCallbacks(musicIdleShift)
    }

    private fun musicInteraction(): Boolean {
        val restored = musicIdle.interact(SystemClock.uptimeMillis(), musicIdleEligible())
        musicIdleOpen.target = 0f
        updateMusicIdle()
        return restored
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        musicInteraction()
        if (!hasWindowFocus) {
            musicIdleOpen.snap(0f)
            removeCallbacks(mediaProgressTick)
            cancelPressedInput()
        } else kick()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateMusicIdle()
        if (visibility != VISIBLE) removeCallbacks(mediaProgressTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(musicIdleCheck)
        removeCallbacks(musicIdleShift)
        removeCallbacks(mediaProgressTick)
        musicIdle.update(SystemClock.uptimeMillis(), false)
        musicIdleOpen.snap(0f)
        cancelPressedInput()
        super.onDetachedFromWindow()
    }

    private fun cancelPressedInput() {
        removeCallbacks(longPress)
        longPressArmed = false
        okDown = false
        idleWakeKey = null
        idleWakeTouch = false
        pressS.snap(0f)
    }

    /** Visuals for artwork named by address (rare: most sessions hand over the image itself). */
    private val uriVisuals = object : LinkedHashMap<String, TrackVisuals>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackVisuals>) = size > 3
    }

    /** The blurred backdrop, large artwork, glow and colours for a track, once its artwork is in. */
    private fun visualsFor(m: NowPlaying): TrackVisuals? {
        if (m.art != null) return host.media().visualsFor(m.key)
        val uri = m.artUri ?: return null
        uriVisuals[uri]?.let { return it }
        val bmp = host.content().art(uri, forMusic = true) ?: return null
        return TrackVisuals.of(bmp)?.also { uriVisuals[uri] = it }
    }

    /** A wide photo of the artist, when the user turned artist photos on and one is found and loaded. */
    private fun photoFor(m: NowPlaying): Bitmap? {
        if (!prefs.artistPhotos || m.artist.isEmpty() || !AppSupport.isMusic(m.pkg)) return null
        val url = host.content().artistPhotoUrl(m.artist) ?: return null
        return host.content().photo(url)
    }

    /** The chip shows unless the hero is already showing the track (resting on the player, or its controls). */
    private fun chipWanted(): Boolean {
        val np = nowPlaying() ?: return false
        if (tiles.isEmpty() || pages.isNotEmpty() || zone == Zone.MEDIA || zone == Zone.QUEUE) return false
        if (zone == Zone.GRID && rowOf(focusIndex) == 0 && tiles.getOrNull(focusIndex)?.app?.pkg == np.pkg) return false
        return true
    }

    // ------------------------------------------------------------------ public API

    /** Apps that arrived while the user was moving a tile; applied when the move ends. */
    private var pendingApps: List<AppEntry>? = null

    fun setApps(apps: List<AppEntry>, keepFocusKey: String? = null) {
        wakes[4]++
        if (moving) {
            // A background package event must not undo a rearrangement in progress.
            pendingApps = apps
            return
        }
        val oldKey = keepFocusKey ?: tiles.getOrNull(focusIndex)?.app?.key
        val old = tiles.associateBy { it.app.key }
        val now = SystemClock.uptimeMillis()
        // A reload brings new AppEntry objects for the same apps: keep each tile (and its
        // springs) and just point it at the fresh entry, so nothing re-animates.
        tiles = apps.mapIndexed { i, a ->
            old[a.key]?.also { it.app = a } ?: Tile(a).also { tile ->
                // No entrance animation: after Android restarts Telum in the background, Home
                // must look as if it never left.
                tile.x.snap(colX(i)); tile.y.snap(rowY(i))
                tile.appear.snap(1f)
            }
        }
        if (tiles.isNotEmpty()) firstLoad = false
        tiles.forEachIndexed { i, tile -> tile.x.target = colX(i); tile.y.target = rowY(i) }
        focusIndex = tiles.indexOfFirst { it.app.key == oldKey }.takeIf { it >= 0 } ?: focusIndex.coerceIn(0, max(0, tiles.size - 1))
        applyMotion()
        // Re-point the glows at the fresh entries (same app, new object).
        val fresh = apps.associateBy { it.key }
        for (g in glows) g.app?.let { a -> fresh[a.key]?.let { g.app = it } }
        wantGlow = wantGlow?.let { fresh[it.key] ?: it }
        if (tiles.isNotEmpty() && zone == Zone.GRID && pages.isEmpty()) {
            val app = tiles[focusIndex].app
            if (!backdropReady) moodTowards(targetMood(app), instant = true, app = app) else focusChanged(app)
        }
        pages.forEach { it.rebuild() }
        kick()
    }

    fun artChanged(entry: AppEntry) {
        wakes[3]++
        if (zone == Zone.GRID && pages.isEmpty() && tiles.getOrNull(focusIndex)?.app?.key == entry.key) {
            val shown = heroShown
            if (shown?.shelf != null && shown.app?.key == entry.key) {
                if (shown.backdrop == 1) shown.backdrop = 0 // re-apply with the app's new colours
            } else {
                moodTowards(targetMood(entry), instant = false, app = entry)
            }
        }
        kick()
    }

    /**
     * Rows from other apps were read again, or a poster arrived. The hero follows on the next
     * frame; an open queue keeps its place, or closes if its row is gone.
     */
    fun contentChanged() {
        wakes[2]++
        if (zone == Zone.QUEUE) {
            val shelf = contentFor(queueApp)
            if (shelf == null) {
                closeQueue()
            } else if (shelf !== queueShelf) {
                queueShelf = shelf
                queueIndex = queueIndex.coerceIn(0, shelf.items.size - 1)
                if (cardFocus.size != shelf.items.size) buildCardSprings(shelf)
            }
        }
        pages.forEach { it.rebuild() }
        kick()
    }

    /** Releases every tile's hold on artwork bitmaps (called when Android asks for memory). */
    fun dropArt() {
        for (tile in tiles) { tile.shader = null; tile.shaderFor = null }
    }

    fun setActive(on: Boolean) {
        active = on
        musicInteraction()
        if (on) {
            lastFrame = 0L
            kick()
        } else {
            removeCallbacks(mediaProgressTick)
            cancelPressedInput()
            musicIdleOpen.snap(0f)
        }
    }

    /** Pressing Home while already home: close everything and return to the first row. */
    fun goHome() {
        musicInteraction()
        closeMenu(); closeSheet()
        if (moving) finishMove()
        if (zone == Zone.QUEUE) closeQueue()
        chipFocused = false
        zone = Zone.GRID
        if (tiles.isNotEmpty() && rowOf(focusIndex) > 0) moveFocusTo(focusIndex % COLS)
        kick()
    }

    fun toast(text: String) {
        toastText = text
        toastUntil = SystemClock.uptimeMillis() + 2600L
        toastS.target = 1f
        kick()
    }

    fun refreshPages() {
        pages.forEach { it.rebuild() }
        kick()
    }

    fun applyWallpaper() {
        if (zone == Zone.TOP || pages.isNotEmpty()) {
            moodTowards(homeMood(), instant = false, app = null)
        } else {
            val focused = tiles.getOrNull(focusIndex)?.app
            val shown = heroShown
            if (shown?.shelf != null) shown.backdrop = 0 // the hero re-applies its backdrop
            else moodTowards(targetMood(focused), instant = false, app = focused)
        }
        kick()
    }

    fun kick() {
        if (!canDrawFrames()) return
        wakes[0]++
        if (traceKicks > 0) { traceKicks--; watchdog.add("Kick while awake", Throwable()) }
        postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------ geometry

    private fun colX(i: Int) = MARGIN + (i % COLS) * (TILE_W + GAP)
    private fun rowY(i: Int) = ROW0 + (i / COLS) * PITCH
    private fun rowOf(i: Int) = i / COLS
    private fun rows() = (tiles.size + COLS - 1) / COLS

    private fun scrollTarget(): Float {
        val r = rowOf(focusIndex)
        if (zone != Zone.GRID || r == 0) return 0f
        return ROW0 + r * PITCH - BROWSE_ROW_Y
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        u = w / W
        buildResources(w, h)
    }

    private fun buildResources(w: Int, h: Int) {
        // A soft falloff disc; the paint colour tints it (ALPHA_8 bitmaps take the paint colour).
        val size = 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val c = Canvas(b)
        val gp = Paint(Paint.ANTI_ALIAS_FLAG)
        gp.shader = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            intArrayOf(0xFF000000.toInt(), 0xB0000000.toInt(), 0x50000000, 0x18000000, 0x00000000),
            floatArrayOf(0f, 0.3f, 0.58f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gp)
        blob = b
        blobShader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // Fine grain over the backdrop hides 8-bit banding in the dark gradients.
        val n = 96
        val noise = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        val pixels = IntArray(n * n) {
            val v = rnd.nextInt(256)
            Color.argb(10, v, v, v)
        }
        noise.setPixels(pixels, 0, n, 0, 0, n, n)
        noiseShader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        // One pre-blurred shadow shared by every tile.
        val tw = px(TILE_W)
        val th = px(TILE_H)
        shadowPad = px(26f)
        val sb = Bitmap.createBitmap((tw + shadowPad * 2).toInt(), (th + shadowPad * 2).toInt(), Bitmap.Config.ALPHA_8)
        val sc = Canvas(sb)
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(px(13f), BlurMaskFilter.Blur.NORMAL); color = Color.BLACK }
        sc.drawRoundRect(RectF(shadowPad, shadowPad, shadowPad + tw, shadowPad + th), px(RADIUS), px(RADIUS), sp)
        shadowBmp = sb

        // Glare and edge gradients are built once in tile-local space; per-frame changes only move
        // them (local matrix) or fade them (paint alpha).
        glareShader = LinearGradient(
            -tw * 0.6f, -th / 2f, tw * 0.6f, th / 2f,
            intArrayOf(0x3DFFFFFF, 0x0DFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.38f, 0.7f), Shader.TileMode.CLAMP,
        )
        edgeShader = LinearGradient(0f, -th / 2f, 0f, th / 2f, 0x59FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP)

        bottomScrim = LinearGradient(0f, h * 0.42f, 0f, h.toFloat(), intArrayOf(0x00000000, 0x66000000, 0xB3000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        topScrim = LinearGradient(0f, 0f, 0f, px(110f), intArrayOf(0x59000000, 0x00000000), null, Shader.TileMode.CLAMP)
        leftScrim = RadialGradient(px(120f), px(195f), px(520f), intArrayOf(0x52000000, 0x00000000), null, Shader.TileMode.CLAMP)
        photoLeft = LinearGradient(0f, 0f, w * 0.65f, 0f, intArrayOf(0xC0000000.toInt(), 0x60000000, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        photoBottom = LinearGradient(0f, h * 0.4f, 0f, h.toFloat(), intArrayOf(0x00000000, 0x99000000.toInt(), 0xE6000000.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        photoTop = LinearGradient(0f, 0f, 0f, px(130f), intArrayOf(0x80000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)

        titleP.textSize = px(40f)
        ctxP.textSize = px(13f)
        tagP.textSize = px(16f)
        metaP.textSize = px(13f)
        labelP.textSize = px(13.5f)
        timeP.textSize = px(19f)
        itemP.textSize = px(15f)
        valueP.textSize = px(14f)
        bigTimeP.textSize = px(64f)
    }

    // ------------------------------------------------------------------ frame loop

    private fun stepAll(dt: Float): Boolean {
        var busy = false
        val now = SystemClock.uptimeMillis()
        for ((i, tile) in tiles.withIndex()) {
            if (tile.appear.target < 1f && now >= tile.appearAt) tile.appear.target = 1f
            busy = tile.x.step(dt) or busy
            busy = tile.y.step(dt) or busy
            tile.focus.target = if (i == focusIndex && zone == Zone.GRID && pages.isEmpty()) 1f else 0f
            busy = tile.focus.step(dt) or busy
            busy = tile.appear.step(dt) or busy || tile.appear.target < 1f
            busy = tile.artFade.step(dt) or busy
        }
        for ((i, s) in topFocus.withIndex()) {
            s.target = if (zone == Zone.TOP && !chipFocused && topIndex == i && pages.isEmpty()) 1f else 0f
            busy = s.step(dt) or busy
        }
        scroll.target = scrollTarget()
        hero.target = if (zone != Zone.GRID || rowOf(focusIndex) == 0) 1f else 0f
        lift.target = if (moving) 1f else 0f
        if (toastText != null && now > toastUntil) toastS.target = 0f
        if (toastS.value < 0.01f && toastS.target == 0f) toastText = null
        // A backdrop change applies once focus has rested; until then frames keep coming to check.
        val waiting = wantMood != null
        if (waiting && now - wantSince >= DWELL_MS) applyMood(wantMood!!, wantGlow)
        val heroWaiting = stepHero(now)
        stepQueue()
        mediaOpen.target = if (zone == Zone.MEDIA) 1f else 0f
        for ((i, s) in mediaFocus.withIndex()) s.target = if (zone == Zone.MEDIA && i == mediaIndex) 1f else 0f
        val chip = chipWanted()
        if (!chip && chipFocused) { chipFocused = false; topIndex = 0 }
        chipShow.target = if (chip) 1f else 0f
        chipFocus.target = if (zone == Zone.TOP && chipFocused && pages.isEmpty()) 1f else 0f
        for (s in arrayOf(mediaOpen, chipShow, chipFocus)) busy = s.step(dt) or busy
        for (s in mediaFocus) busy = s.step(dt) or busy
        // Highlight pills follow focus from this frame on (setting them while drawing left the
        // pill one press behind once the frame loop stopped between presses).
        menuPill.target = menuFocus.toFloat()
        pages.lastOrNull()?.let { sheetPill.target = it.focus.toFloat() }
        for (s in arrayOf(scroll, hero, moodT, pressS, nudgeX, nudgeY, lift, menuOpen, menuPill, sheetOpen, sheetPill, pageSwap, toastS, queueOpen, queueScroll)) {
            busy = s.step(dt) or busy
        }
        for (s in cardFocus) busy = s.step(dt) or busy
        for (l in heroLayers) { busy = l.alpha.step(dt) or busy; busy = l.rise.step(dt) or busy }
        heroLayers.removeAll { it !== heroShown && it.alpha.target == 0f && it.alpha.value < 0.002f }
        for (g in glows) busy = g.alpha.step(dt) or busy
        glows.removeAll { it.alpha.target == 0f && it.alpha.value < 0.002f }
        if (zone != Zone.QUEUE && queueOpen.value < 0.01f && queueShelf != null) { queueShelf = null; queueApp = null; artShaders.clear() }
        for (p in pages) busy = p.scroll.step(dt) or busy
        if (menuOpen.target == 0f && menuOpen.value < 0.01f) menuItems = emptyList()
        if (sheetOpen.target == 0f && sheetOpen.value < 0.01f && pages.isNotEmpty()) pages.clear()
        // Keep frames coming while a toast is up, so it is dismissed on time even when idle.
        return busy || waiting || heroWaiting || toastText != null
    }

    // ------------------------------------------------------------------ hero choice

    /** The row the focused first-row app publishes, if the user shows what they're watching. */
    private fun contentFor(app: AppEntry?): Shelf? = app?.let { host.content().shelfFor(it.pkg) }

    /**
     * Picks what the hero should show and commits it once focus has rested for [DWELL_MS]:
     * the greeting, or a programme from the focused app's row (in the queue, the focused card).
     * Returns true while a change is waiting.
     */
    private fun stepHero(now: Long): Boolean {
        // Until the apps are listed, show no hero: a restarted Home then appears in one piece
        // instead of a greeting that swaps to a programme or track a moment later.
        if (tiles.isEmpty()) return false
        var app: AppEntry? = null
        var shelf: Shelf? = null
        var index = 0
        var media: NowPlaying? = null
        when (zone) {
            Zone.TOP -> {}
            Zone.QUEUE -> { app = queueApp; shelf = queueShelf; index = queueIndex }
            Zone.MEDIA -> { app = mediaApp; media = nowPlaying(); if (media == null) app = null }
            Zone.GRID -> {
                // Lower rows hide the hero; it keeps whatever it showed.
                if (rowOf(focusIndex) > 0 && heroShown != null) return false
                app = tiles.getOrNull(focusIndex)?.app
                // The app that is playing shows its track; otherwise its row of programmes, if any.
                val np = nowPlaying()
                if (app != null && np != null && np.pkg == app.pkg) media = np
                else shelf = contentFor(app)
                if (shelf == null && media == null) app = null
            }
        }
        val shown = heroShown
        if (shown == null) {
            commitHero(app, shelf, index, media, instant = true)
            return false
        }
        if (sameHero(shown, shelf, index, media)) {
            // Same programme or track with fresh data (a re-read row, play or pause): keep the layer.
            if (shelf != null && shown.shelf !== shelf) { shown.shelf = shelf; shown.texts() }
            if (media != null && shown.media !== media) {
                if (shown.media?.art !== media.art || shown.media?.artUri != media.artUri) shown.backdrop = 0
                shown.media = media
                shown.texts()
            }
            wantHeroSig = null
            heroBackdrop(shown)
            return false
        }
        val sig = when {
            media != null -> "media|${media.key}"
            shelf != null -> "${shelf.pkg}|${shelf.name}|$index|${shelf.items.getOrNull(index)?.key}"
            else -> "ambient"
        }
        if (sig != wantHeroSig) { wantHeroSig = sig; wantHeroSince = now }
        if (now - wantHeroSince < DWELL_MS) return true
        wantHeroSig = null
        commitHero(app, shelf, index, media, instant = false)
        return false
    }

    private fun sameHero(l: HeroLayer, shelf: Shelf?, index: Int, media: NowPlaying?): Boolean {
        if (media != null || l.media != null) return media != null && l.media?.key == media.key
        val ls = l.shelf
        if (shelf == null || ls == null) return shelf == null && ls == null
        return ls.pkg == shelf.pkg && ls.name == shelf.name && l.index == index &&
            l.programme?.key == shelf.items.getOrNull(index)?.key && l.programme?.position == shelf.items.getOrNull(index)?.position
    }

    private fun commitHero(app: AppEntry?, shelf: Shelf?, index: Int, media: NowPlaying?, instant: Boolean) {
        val layer = HeroLayer(app, shelf, index, media).apply { texts() }
        val full = prefs.motion
        if (instant) layer.alpha.snap(1f)
        else if (full) { layer.rise.snap(8f); layer.rise.target = 0f }
        heroLayers += layer
        heroShown = layer
        for (l in heroLayers) {
            val on = l === layer
            l.alpha.tune(if (!full) 400f else if (on) 90f else 200f, 1f)
            if (!instant || !on) l.alpha.target = if (on) 1f else 0f
        }
        // Start loading the first posters now, so the queue rarely shows placeholders.
        if (shelf != null) for (p in shelf.items.take(8)) p.art?.let { host.content().art(it) }
        heroBackdrop(layer)
    }

    /** A programme's or track's backdrop: the app's colours at once, then the artwork's when it has loaded. */
    private fun heroBackdrop(l: HeroLayer) {
        val app = l.app ?: return
        if (l.ambient || zone == Zone.TOP || pages.isNotEmpty() || l.backdrop == 2) return
        val dynamic = prefs.wallpaper == "dynamic"
        val media = l.media
        if (media != null) {
            val v = visualsFor(media)
            if (v != null) {
                if (dynamic) applyMood(v.mood, app, null, v.glow, "media:${media.key}:${System.identityHashCode(v.glow)}")
                else applyMood(targetMood(app), null)
                l.backdrop = 2
            } else if (l.backdrop == 0) {
                applyMood(targetMood(app), if (dynamic) app else null)
                l.backdrop = 1
            }
            return
        }
        val art = l.programme?.art
        val glow = art?.let { host.content().glow(it) }
        when {
            glow != null -> {
                applyMood(if (dynamic) glow.second else targetMood(app), if (dynamic) app else null, if (dynamic) art else null)
                l.backdrop = 2
            }
            l.backdrop == 0 -> {
                applyMood(targetMood(app), if (dynamic) app else null)
                l.backdrop = 1
            }
        }
    }

    // ------------------------------------------------------------------ queue

    private fun openQueue(app: AppEntry, shelf: Shelf) {
        queueApp = app
        queueShelf = shelf
        queueIndex = 0
        queueScroll.snap(0f)
        buildCardSprings(shelf)
        zone = Zone.QUEUE
        playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
        kick()
    }

    private fun closeQueue() {
        queueIndex = 0
        zone = Zone.GRID
        kick()
    }

    private fun openMedia(app: AppEntry) {
        mediaApp = app
        mediaIndex = 1
        zone = Zone.MEDIA
        playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
        kick()
    }

    private fun closeMedia() {
        zone = Zone.GRID
        kick()
    }

    private fun buildCardSprings(shelf: Shelf) {
        val full = prefs.motion
        cardFocus = Array(shelf.items.size) { Spring(0f, if (full) 300f else 900f, if (full) 0.9f else 1f) }
    }

    private fun cardSize(shelf: Shelf): Pair<Float, Float> = if (shelf.portrait) 66f to 99f else 176f to 99f
    private fun cardGap(shelf: Shelf): Float = if (shelf.portrait) 14f else 16f

    private fun stepQueue() {
        queueOpen.target = if (zone == Zone.QUEUE) 1f else 0f
        for ((i, s) in cardFocus.withIndex()) s.target = if (zone == Zone.QUEUE && i == queueIndex) 1f else 0f
        val shelf = queueShelf ?: return
        // Keep the focused card inside the margins.
        val (w, _) = cardSize(shelf)
        val pitch = w + cardGap(shelf)
        val left = MARGIN + queueIndex * pitch
        var target = queueScroll.target
        if (left + w - target > W - MARGIN - 4f) target = left + w - (W - MARGIN - 4f)
        if (left - target < MARGIN) target = left - MARGIN
        queueScroll.target = target.coerceAtLeast(0f)
    }

    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = now
        val up = SystemClock.uptimeMillis()
        // A gap this long means the loop had stopped (a playing track's once-a-second frames count
        // as stops); a slow frame inside a loop must not start a fresh count and log again.
        if (up - prevDraw > 500L) { awakeSince = up; awakeLogged = false; wakes.fill(0) }
        prevDraw = up
        // Nothing drifts on its own: once the springs settle the frame loop stops.
        if (moving && prefs.motion) t += dt
        updateMusicIdle()
        val idleBusy = musicIdleOpen.step(dt)
        val idle = musicIdleOpen.value.coerceIn(0f, 1f)
        val homeVisible = idle < 0.999f
        val busy = if (homeVisible) stepAll(dt) else false

        if (homeVisible) {
            val layer = if (idle > 0.001f) cv.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), ((1f - idle) * 255).toInt()) else cv.save()
            cv.translate(0f, px(48f) * idle)
            drawBackdrop(cv)
            drawHero(cv)
            drawQueue(cv)
            drawMediaControls(cv)
            drawGrid(cv)
            drawTopBar(cv)
            drawToast(cv)
            drawMenu(cv)
            drawSheet(cv)
            cv.restoreToCount(layer)
        }
        if (idle > 0.001f) drawIdleMusic(cv, idle)

        // Drawing the sheet can move a target (it scrolls the focused row into view), so check the
        // springs again: a target set after the step must still get its frames.
        val again = idleBusy || (homeVisible && (busy || moving || !sheetPill.atRest || !menuPill.atRest || pages.any { !it.scroll.atRest }))
        if (!awakeLogged && up - max(awakeSince, lastKeyAt) > 5000L) {
            awakeLogged = true
            traceKicks = 3
            watchdog.add("Frames for 5 s without input. Busy: ${busyReport()}; again=$again; wakes=${wakes.joinToString("/")}")
        }
        removeCallbacks(mediaProgressTick)
        if (again && canDrawFrames()) {
            postInvalidateOnAnimation()
        } else {
            lastFrame = 0L
            // A playing track's position moves on: one frame a second while the hero shows it.
            val m = heroShown?.media
            if (canDrawFrames() && !musicIdle.dimmed && m != null && m.playing && m.duration > 0 && hero.value > 0.5f && pages.isEmpty()) {
                postDelayed(mediaProgressTick, 1000L - SystemClock.uptimeMillis() % 1000L)
            }
        }
    }

    /** Dark artwork, no progress bar or clock. Infrequent shifts reduce fixed bright details. */
    private fun drawIdleMusic(cv: Canvas, alpha: Float) {
        val track = nowPlaying() ?: return
        val layer = if (alpha < 0.999f) cv.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt()) else cv.save()
        cv.drawColor(Color.BLACK)
        val art = photoFor(track) ?: track.art ?: track.artUri?.let { host.content().art(it, forMusic = true) }
        if (art != null) {
            val scale = max(width.toFloat() / art.width, height.toFloat() / art.height) * 1.08f
            val dx = px(if (idleShift and 1 == 0) -8f else 8f)
            val dy = px(if (idleShift and 2 == 0) -6f else 6f)
            photoMatrix.setScale(scale, scale)
            photoMatrix.postTranslate((width - art.width * scale) / 2f + dx, (height - art.height * scale) / 2f + dy)
            bmpPaint.alpha = 68
            cv.drawBitmap(art, photoMatrix, bmpPaint)
            bmpPaint.alpha = 255
        }
        // The text changes corners and inset each minute, even during a very long track.
        val right = idleShift and 1 != 0
        val top = idleShift and 2 != 0
        val inset = px(MARGIN + if (idleShift and 4 != 0) 18f else 0f)
        val x = if (right) width - inset else inset
        val y = if (top) inset + px(22f) else height - inset - px(28f)
        idleTitleP.typeface = titleP.typeface
        idleTitleP.textSize = px(23f)
        idleTitleP.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        idleTitleP.color = 0x78FFFFFF
        idleSubtitleP.typeface = tagP.typeface
        idleSubtitleP.textSize = px(14f)
        idleSubtitleP.textAlign = idleTitleP.textAlign
        idleSubtitleP.color = 0x60FFFFFF
        val maxWidth = width - inset * 2f
        cv.drawText(fit(track.title, idleTitleP, maxWidth), x, y, idleTitleP)
        cv.drawText(fit(track.subtitle, idleSubtitleP, maxWidth), x, y + px(24f), idleSubtitleP)
        cv.restoreToCount(layer)
    }

    /** What is keeping the frame loop going, for the watchdog's log line (names only, no titles). */
    private fun busyReport(): String {
        val out = ArrayList<String>()
        fun s(name: String, sp: Spring) { if (!sp.atRest) out += "$name ${sp.value}->${sp.target}" }
        tiles.forEachIndexed { i, t -> s("tile$i.x", t.x); s("tile$i.y", t.y); s("tile$i.focus", t.focus); s("tile$i.appear", t.appear); s("tile$i.artFade", t.artFade) }
        topFocus.forEachIndexed { i, sp -> s("top$i", sp) }
        mediaFocus.forEachIndexed { i, sp -> s("mediaFocus$i", sp) }
        cardFocus.forEachIndexed { i, sp -> s("card$i", sp) }
        heroLayers.forEachIndexed { i, l -> s("heroLayer$i.alpha", l.alpha); s("heroLayer$i.rise", l.rise) }
        glows.forEachIndexed { i, g -> s("glow$i.alpha", g.alpha) }
        pages.forEachIndexed { i, p -> s("page$i.scroll", p.scroll) }
        for ((n, sp) in listOf("scroll" to scroll, "hero" to hero, "moodT" to moodT, "press" to pressS, "nudgeX" to nudgeX,
            "nudgeY" to nudgeY, "lift" to lift, "menuOpen" to menuOpen, "menuPill" to menuPill, "sheetOpen" to sheetOpen,
            "sheetPill" to sheetPill, "pageSwap" to pageSwap, "toast" to toastS, "queueOpen" to queueOpen,
            "queueScroll" to queueScroll, "mediaOpen" to mediaOpen, "chipShow" to chipShow, "chipFocus" to chipFocus)) s(n, sp)
        if (wantMood != null) out += "mood waiting"
        wantHeroSig?.let { out += "hero waiting (${it.substringBefore('|')})" }
        if (toastText != null) out += "toast up"
        if (moving) out += "moving"
        out += "zone $zone, layers ${heroLayers.size}, glows ${glows.size}"
        return out.joinToString(", ")
    }

    // ------------------------------------------------------------------ backdrop

    private fun currentMood(): Mood = Mood.lerp(moodFrom, moodTo, moodT.value)

    private fun targetMood(app: AppEntry?): Mood =
        if (prefs.wallpaper == "dynamic") app?.mood ?: Mood.DEFAULT else Palette.theme(prefs.wallpaper)

    private var backdropReady = false

    /**
     * Asks the backdrop to follow [app] (or a fixed theme). The change waits until focus has
     * rested for [DWELL_MS], so moving quickly across the row only moves the tiles.
     */
    private fun moodTowards(mood: Mood, instant: Boolean, app: AppEntry? = null) {
        val glowApp = if (prefs.wallpaper == "dynamic") app else null
        if (instant) {
            wantMood = null; wantGlow = null
            moodFrom = mood; moodTo = mood; moodT.snap(1f)
            glows.clear()
            glowKey = glowApp?.key
            glowApp?.let { glows += Glow(it.key, it, null).apply { alpha.snap(1f) } }
            backdropReady = true
            return
        }
        if (mood == moodTo && glowApp?.key == glowKey) {
            wantMood = null; wantGlow = null // focus came back before the change applied
            return
        }
        if (mood != wantMood || glowApp?.key != wantGlow?.key) {
            wantMood = mood; wantGlow = glowApp
            wantSince = SystemClock.uptimeMillis()
        }
        kick()
    }

    /**
     * Cross-fades the colours from where they are now, and each glow from its own opacity. The
     * glow is the app's own, or with [art] the poster of the programme the hero shows.
     */
    private fun applyMood(mood: Mood, glowApp: AppEntry?, art: String? = null, fixed: Bitmap? = null, fixedKey: String? = null) {
        wantMood = null; wantGlow = null
        moodFrom = currentMood()
        moodTo = mood
        moodT.snap(0f)
        moodT.target = 1f
        val key = if (fixed != null) fixedKey else if (art != null) "art:$art" else glowApp?.key
        glowKey = key
        val stiffness = if (prefs.motion) 40f else 400f
        if (key != null && glows.none { it.key == key }) glows += Glow(key, glowApp, art, fixed).apply { alpha.tune(stiffness, 1f) }
        for (g in glows) {
            if (g.key == key && glowApp != null) g.app = glowApp
            g.alpha.target = if (g.key == key) 1f else 0f
        }
        backdropReady = true
    }

    /**
     * The backdrop is nothing but soft gradients, so it is painted into an offscreen layer at a
     * quarter of the screen's width and height and scaled up: it looks the same and costs a
     * sixteenth of the GPU fill, which matters if the SHIELD composes the UI at 4K. Only the
     * fine grain is drawn at full resolution.
     */
    private val lightNode = RenderNode("telum-light")

    private fun drawBackdrop(cv: Canvas) {
        if (cv.isHardwareAccelerated) {
            val lw = ceil(width / LIGHT_SCALE).toInt()
            val lh = ceil(height / LIGHT_SCALE).toInt()
            lightNode.setPosition(0, 0, lw, lh)
            lightNode.setUseCompositingLayer(true, null)
            val rc = lightNode.beginRecording(lw, lh)
            rc.scale(1f / LIGHT_SCALE, 1f / LIGHT_SCALE)
            paintLight(rc)
            lightNode.endRecording()
            cv.save()
            cv.scale(LIGHT_SCALE, LIGHT_SCALE)
            cv.drawRenderNode(lightNode)
            cv.restore()
        } else {
            paintLight(cv)
        }
        drawArtistPhotos(cv)
        fill.shader = noiseShader
        cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
    }

    private fun paintLight(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mood = currentMood()
        cv.drawColor(mood.base)

        val shader = blobShader ?: return
        blobPaint.shader = shader
        fun blob(cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
            m.setScale(radius * 2f / 128f, radius * 2f / 128f)
            m.postTranslate(cx - radius, cy - radius)
            shader.setLocalMatrix(m)
            blobPaint.color = withAlpha(color, alpha)
            cv.drawCircle(cx, cy, radius, blobPaint)
        }
        // Fixed positions: a still backdrop lets the frame loop stop as soon as motion settles.
        blob(w * 0.18f, h * 0.24f, w * 0.6f, mood.a, 0.9f)
        blob(w * 0.94f, h * 0.22f, w * 0.5f, mood.b, 0.8f)
        blob(w * 0.73f, h * 0.86f, w * 0.62f, mood.c, 0.9f)
        blob(w * -0.01f, h * 0.98f, w * 0.42f, lerpColor(mood.a, mood.b, 0.5f), 0.5f)
        blobPaint.shader = null

        // The focused app's (or poster's) colours, blurred into light behind the hero (Apple Music style).
        val glowStrength = 0.4f + 0.6f * hero.value
        for (g in glows) g.bitmap()?.let { drawGlow(cv, it, g.alpha.value.coerceIn(0f, 1f) * glowStrength) }

        // Music: the album art itself, blurred across the whole screen (this layer is drawn at a
        // quarter of the resolution, which costs nothing for a picture that is meant to be soft).
        if (prefs.wallpaper == "dynamic") {
            for (l in heroLayers) {
                val m = l.media ?: continue
                val a = l.alpha.value.coerceIn(0f, 1f) * hero.value
                if (a < 0.01f) continue
                val v = visualsFor(m) ?: continue
                rf.set(0f, 0f, w, h)
                bmpPaint.alpha = (a * 255).toInt()
                cv.drawBitmap(v.wash, null, rf, bmpPaint)
                bmpPaint.alpha = 255
            }
        }

        // Darken for legibility; deeper while browsing the grid, like the dimmed wallpaper on tvOS.
        val browse = 1f - hero.value
        fill.shader = null
        fill.color = withAlpha(Color.BLACK, 0.12f + 0.38f * browse)
        cv.drawRect(0f, 0f, w, h, fill)
        fill.shader = leftScrim; cv.drawRect(0f, 0f, w, h, fill)
        fill.shader = bottomScrim; cv.drawRect(0f, 0f, w, h, fill)
        fill.shader = topScrim; cv.drawRect(0f, 0f, w, px(110f), fill)
        fill.shader = null
    }

    private val photoMatrix = Matrix()

    /**
     * Artist photos (opt-in) fill the screen at full resolution behind the hero, under their own
     * shading so the title and the apps stay legible over any picture.
     */
    private fun drawArtistPhotos(cv: Canvas) {
        if (!prefs.artistPhotos) return
        for (l in heroLayers) {
            val m = l.media ?: continue
            val a = l.alpha.value.coerceIn(0f, 1f) * hero.value
            if (a < 0.01f) continue
            val photo = photoFor(m) ?: continue
            val s = max(width.toFloat() / photo.width, height.toFloat() / photo.height)
            photoMatrix.setScale(s, s)
            photoMatrix.postTranslate((width - photo.width * s) / 2f, (height - photo.height * s) / 2f)
            bmpPaint.alpha = (a * 255).toInt()
            cv.drawBitmap(photo, photoMatrix, bmpPaint)
            bmpPaint.alpha = 255
            fill.alpha = (a * 255).toInt()
            for (shade in arrayOf(photoLeft, photoBottom, photoTop)) {
                fill.shader = shade ?: continue
                cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            }
            fill.shader = null
            fill.alpha = 255
        }
    }

    private fun drawGlow(cv: Canvas, g: Bitmap, alpha: Float) {
        if (alpha < 0.01f) return
        val w = width.toFloat()
        val h = height.toFloat()
        val gw = w * 0.95f
        val gh = gw * 9f / 16f
        val left = w * 0.265f
        val top = -h * 0.145f
        rf.set(left, top, left + gw, top + gh)
        bmpPaint.alpha = (alpha * 0.85f * 255).toInt()
        cv.drawBitmap(g, null, rf, bmpPaint)
        bmpPaint.alpha = 255
    }

    // ------------------------------------------------------------------ hero

    /**
     * Focus landed on [app]: the backdrop follows once focus rests (the top bar and Control center
     * keep the time of day). A first-row app with a row of programmes gets its backdrop from the
     * hero instead, so nothing is scheduled here.
     */
    private fun focusChanged(app: AppEntry) {
        if (zone != Zone.GRID || pages.isNotEmpty()) return
        if (rowOf(focusIndex) == 0 && contentFor(app) != null) {
            wantMood = null; wantGlow = null
            heroShown?.let { if (it.shelf != null && it.app?.key == app.key && it.backdrop == 0) heroBackdrop(it) }
            return
        }
        moodTowards(targetMood(app), instant = false, app = app)
    }

    /**
     * The hero: the ambient greeting (date, greeting, weather), or a programme from the focused
     * app's row. It fades out while browsing the lower rows; changes cross-fade from the visible
     * state once focus rests.
     */
    private fun drawHero(cv: Canvas) {
        val alpha = hero.value * (1f - sheetOpen.value) * (1f - 0.85f * menuOpen.value)
        if (alpha < 0.01f) return
        val dy = (1f - hero.value) * px(-30f) - px(scroll.value) * 0.35f
        for (l in heroLayers) {
            val la = alpha * l.alpha.value.coerceIn(0f, 1f)
            if (la < 0.01f) continue
            when {
                l.media != null -> drawMediaHero(cv, l, la, dy + px(l.rise.value))
                l.shelf == null -> heroText(cv, texts.date(), texts.greeting(), texts.weatherLine(), la, dy + px(l.rise.value))
                else -> drawProgrammeHero(cv, l, la, dy + px(l.rise.value))
            }
        }
    }

    /**
     * Context line ("Stremio · Continue Watching"), title, episode line, then progress and the
     * queue hint, with the poster to the right. Opening the queue moves the block up and hands
     * the poster over to the cards.
     */
    private fun drawProgrammeHero(cv: Canvas, l: HeroLayer, alpha: Float, dy: Float) {
        val p = l.programme ?: return
        val q = queueOpen.value.coerceIn(0f, 1f)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val x = px(MARGIN)
        val base = px(HERO_BASE - 28f) + dy - px(38f) * q
        var textMax = px(620f)
        p.art?.let { uri ->
            val aw = if (p.portrait) 113f else 236f
            val ah = if (p.portrait) 163f else 133f
            val left = px(W - MARGIN - aw)
            textMax = min(textMax, left - x - px(28f))
            val pa = alpha * (1f - q)
            if (pa > 0.01f) {
                val top = px(if (p.portrait) 96f else 126f) + dy
                drawCardShadow(cv, left, top, px(aw), px(ah), 1f, pa)
                drawArt(cv, uri, p.title, left, top, px(aw), px(ah), px(10f), pa, 1f)
            }
        }
        ctxP.alpha = (a * 0.62f).toInt()
        cv.drawText(fit(l.context, ctxP, textMax), x + px(1f), base - px(42f), ctxP)
        val s = 1f - 0.2f * q
        titleP.alpha = a
        cv.save()
        cv.scale(s, s, x, base)
        cv.drawText(fit(p.title, titleP, textMax / s), x, base, titleP)
        cv.restore()
        if (p.detail.isNotEmpty()) {
            tagP.alpha = (a * 0.9f).toInt()
            cv.drawText(fit(p.detail, tagP, textMax), x + px(1f), base + px(25f) - px(3f) * q, tagP)
        }
        val fade = 1f - q
        if (fade < 0.01f) return
        val fa = (a * fade).toInt()
        metaP.textSize = px(13f)
        var hintY = base + px(56f)
        if (p.hasProgress && l.remaining.isNotEmpty()) {
            val y = base + px(52f)
            metaP.alpha = (fa * 0.8f).toInt()
            cv.drawText(l.remaining, x + px(1f), y, metaP)
            val bx = x + px(1f) + metaP.measureText(l.remaining) + px(12f)
            val by = y - px(5f)
            rf3.set(bx, by, bx + px(180f), by + px(3f))
            fill.color = withAlpha(Color.WHITE, 0.22f * alpha * fade)
            cv.drawRoundRect(rf3, px(1.5f), px(1.5f), fill)
            rf3.right = bx + px(180f) * (p.position.toFloat() / p.duration).coerceIn(0.03f, 1f)
            fill.color = withAlpha(Color.WHITE, 0.92f * alpha * fade)
            cv.drawRoundRect(rf3, px(1.5f), px(1.5f), fill)
            hintY = base + px(80f)
        }
        // "⌃ 20 titles": Up opens the queue. Shown only where a queue exists.
        val hy = hintY - px(4f)
        cv.save()
        cv.rotate(-90f, x + px(6f), hy)
        Glyphs.draw(cv, "chevron", x + px(6f), hy, px(12f), withAlpha(Color.WHITE, 0.56f * alpha * fade))
        cv.restore()
        metaP.textSize = px(12.5f)
        metaP.alpha = (fa * 0.56f).toInt()
        cv.drawText(l.count, x + px(18f), hintY, metaP)
        metaP.textSize = px(13f)
    }

    /** "1:23" or "1:02:03". */
    private fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /**
     * The track the focused app is playing: "Spotify · Now playing", title, artist and album, the
     * position, and the artwork as a square card. Up shows the controls under it.
     */
    private fun drawMediaHero(cv: Canvas, l: HeroLayer, alpha: Float, dy: Float) {
        val m = l.media ?: return
        val q = mediaOpen.value.coerceIn(0f, 1f)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val x = px(MARGIN)
        val base = px(HERO_BASE - 28f) + dy - px(38f) * q
        // The artwork, large, melting into the right of the screen over its own blurred backdrop.
        // With artist photos on, the photo fills the screen instead (see drawArtistPhotos).
        val textMax = px(600f)
        if (photoFor(m) == null) {
            visualsFor(m)?.let { v ->
                val side = px(380f)
                rf3.set(width - side, dy - px(8f), width.toFloat(), dy - px(8f) + side)
                bmpPaint.alpha = a
                cv.drawBitmap(v.faded, null, rf3, bmpPaint)
                bmpPaint.alpha = 255
            }
        }
        ctxP.alpha = (a * 0.62f).toInt()
        cv.drawText(fit(l.context, ctxP, textMax), x + px(1f), base - px(42f), ctxP)
        val s = 1f - 0.2f * q
        titleP.alpha = a
        cv.save()
        cv.scale(s, s, x, base)
        cv.drawText(fit(m.title, titleP, textMax / s), x, base, titleP)
        cv.restore()
        if (m.subtitle.isNotEmpty()) {
            tagP.alpha = (a * 0.9f).toInt()
            cv.drawText(fit(m.subtitle, tagP, textMax), x + px(1f), base + px(25f) - px(3f) * q, tagP)
        }
        val fade = 1f - q
        if (fade < 0.01f) return
        val fa = (a * fade).toInt()
        metaP.textSize = px(13f)
        metaP.fontFeatureSettings = "'tnum'"
        var hintY = base + px(56f)
        if (m.duration > 0) {
            val y = base + px(52f)
            val pos = m.positionNow()
            val text = "${clock(pos)} / ${clock(m.duration)}"
            metaP.alpha = (fa * 0.8f).toInt()
            cv.drawText(text, x + px(1f), y, metaP)
            val bx = x + px(1f) + metaP.measureText(text) + px(12f)
            val by = y - px(5f)
            rf3.set(bx, by, bx + px(180f), by + px(3f))
            fill.color = withAlpha(Color.WHITE, 0.22f * alpha * fade)
            cv.drawRoundRect(rf3, px(1.5f), px(1.5f), fill)
            rf3.right = bx + px(180f) * (pos.toFloat() / m.duration).coerceIn(0.01f, 1f)
            fill.color = withAlpha(Color.WHITE, 0.92f * alpha * fade)
            cv.drawRoundRect(rf3, px(1.5f), px(1.5f), fill)
            hintY = base + px(80f)
        }
        metaP.fontFeatureSettings = null
        val hy = hintY - px(4f)
        cv.save()
        cv.rotate(-90f, x + px(6f), hy)
        Glyphs.draw(cv, "chevron", x + px(6f), hy, px(12f), withAlpha(Color.WHITE, 0.56f * alpha * fade))
        cv.restore()
        metaP.textSize = px(12.5f)
        metaP.alpha = (fa * 0.56f).toInt()
        cv.drawText("Controls", x + px(18f), hintY, metaP)
        metaP.textSize = px(13f)
    }

    /** Previous, play or pause, next: round buttons under the track, like the top bar's. */
    private fun drawMediaControls(cv: Canvas) {
        val open = mediaOpen.value.coerceIn(0f, 1f)
        if (open < 0.01f) return
        val m = heroShown?.media ?: nowPlaying() ?: return
        val alpha = open * (1f - sheetOpen.value)
        val cy = px(214f + 10f * (1f - open))
        val names = arrayOf("media-prev", if (m.playing) "media-pause" else "media-play", "media-next")
        val enabled = booleanArrayOf(m.canPrevious, true, m.canNext)
        val radius = floatArrayOf(20f, 26f, 20f)
        var cx = MARGIN
        for (i in 0..2) {
            cx += radius[i]
            val f = mediaFocus[i].value.coerceIn(0f, 1.2f)
            val r = px(radius[i]) * (1f + 0.12f * f)
            val ccx = px(cx)
            if (f > 0.02f) {
                val full = px(radius[i]) * 1.12f
                val blur = px(8f)
                val bmp = softShadow(full * 2f, full * 2f, full, blur)
                val k = r / full
                val half = (full + blur * 2f) * k
                rf3.set(ccx - half, cy - half + px(4f) * f, ccx + half, cy + half + px(4f) * f)
                bmpPaint.alpha = (0.4f * f.coerceAtMost(1f) * alpha * 255).toInt()
                cv.drawBitmap(bmp, null, rf3, bmpPaint)
                bmpPaint.alpha = 255
            }
            fill.color = lerpColor(withAlpha(Color.WHITE, 0.14f * alpha), withAlpha(Color.WHITE, alpha), f.coerceAtMost(1f))
            cv.drawCircle(ccx, cy, r, fill)
            val glyphAlpha = if (enabled[i]) 1f else 0.35f
            Glyphs.draw(cv, names[i], ccx, cy, px(if (i == 1) 20f else 16f) * (1f + 0.1f * f),
                lerpColor(withAlpha(Color.WHITE, 0.92f * alpha * glyphAlpha), withAlpha(0xFF16181D.toInt(), alpha * glyphAlpha), f.coerceAtMost(1f)))
            cx += radius[i] + 18f
        }
        // What OK does on the focused button.
        val label = when (mediaIndex) { 0 -> "Previous"; 2 -> "Next"; else -> if (m.playing) "Pause" else "Play" }
        labelP.textSize = px(12.5f)
        labelP.alpha = (alpha * 255).toInt()
        val lx = MARGIN + when (mediaIndex) { 0 -> 20f; 1 -> 20f + 18f + 20f + 26f; else -> 20f + 18f + 20f + 26f + 26f + 18f + 20f }
        cv.drawText(label, px(lx), cy + px(radius[mediaIndex]) + px(22f), labelP)
    }

    /** One cached soft shadow per card size, scaled with the card (never a new blur per frame). */
    private fun drawCardShadow(cv: Canvas, l: Float, t: Float, w: Float, h: Float, scale: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val blur = px(10f)
        val bmp = softShadow(w, h, px(10f), blur)
        val pad = (blur * 2f).toInt().toFloat()
        val cx = l + w / 2f
        val cy = t + h / 2f + px(6f)
        val hw = (w / 2f + pad) * scale
        val hh = (h / 2f + pad) * scale
        rf3.set(cx - hw, cy - hh, cx + hw, cy + hh)
        bmpPaint.alpha = (alpha * 0.55f * 255).toInt()
        cv.drawBitmap(bmp, null, rf3, bmpPaint)
        bmpPaint.alpha = 255
    }

    /**
     * Published artwork, centre-cropped into a rounded rect, or a quiet placeholder (with the title
     * on wide cards) until it loads. Loading starts on first use.
     */
    private fun drawArt(cv: Canvas, uri: String?, title: String, l: Float, t: Float, w: Float, h: Float, r: Float, alpha: Float, bright: Float) {
        rf3.set(l, t, l + w, t + h)
        val bmp = uri?.let { host.content().art(it) }
        if (bmp == null) {
            fill.color = withAlpha(lerpColor(Color.BLACK, 0xFF2A2E38.toInt(), bright), alpha)
            cv.drawRoundRect(rf3, r, r, fill)
            if (w > h) {
                labelP.textSize = px(12f)
                labelP.alpha = (alpha * bright * 180).toInt()
                cv.drawText(fit(title, labelP, w - px(16f)), l + w / 2f, t + h / 2f + px(4f), labelP)
            }
            return
        }
        val cached = artShaders[uri]
        val shader = if (cached != null && cached.first === bmp) cached.second
            else BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { artShaders[uri] = bmp to it }
        val s = max(w / bmp.width, h / bmp.height)
        m.setScale(s, s)
        m.postTranslate(l + (w - bmp.width * s) / 2f, t + (h - bmp.height * s) / 2f)
        shader.setLocalMatrix(m)
        artPaint.shader = shader
        artPaint.alpha = (alpha * 255).toInt()
        artPaint.colorFilter = darken(bright)
        cv.drawRoundRect(rf3, r, r, artPaint)
        artPaint.shader = null
        artPaint.colorFilter = null
    }

    /** The queue: the app's programmes as cards above the (unmoved) app row. */
    private fun drawQueue(cv: Canvas) {
        val shelf = queueShelf ?: return
        val open = queueOpen.value.coerceIn(0f, 1f)
        if (open < 0.01f) return
        val alpha = open * (1f - sheetOpen.value) * (1f - 0.85f * menuOpen.value)
        val (cw, ch) = cardSize(shelf)
        val pitch = cw + cardGap(shelf)
        val top = 174f + 10f * (1f - open)
        val scrollX = queueScroll.value
        val r = px(9f)
        // Unfocused first, then the focused card on top so its lift overlaps its neighbours.
        for (pass in 0..1) {
            for ((i, p) in shelf.items.withIndex()) {
                val f = cardFocus.getOrNull(i)?.value ?: 0f
                val focused = i == queueIndex
                if ((pass == 0) == focused) continue
                val left = MARGIN + i * pitch - scrollX
                if (left > W || left + cw < 0f) continue
                val scale = 1f + 0.06f * f
                val w = px(cw) * scale
                val h = px(ch) * scale
                val l = px(left + cw / 2f) - w / 2f
                val t = px(top + ch / 2f) - h / 2f
                // Cards fade out past the margins instead of being cut off.
                val edge = ((left + cw - MARGIN + 30f) / 30f).coerceIn(0f, 1f) * ((W - MARGIN + 30f - left) / 30f).coerceIn(0f, 1f)
                val ca = alpha * edge
                if (ca < 0.01f) continue
                drawCardShadow(cv, px(left), px(top), px(cw), px(ch), scale, ca * (0.45f + 0.55f * f))
                drawArt(cv, p.art, p.title, l, t, w, h, r * scale, ca, 0.74f + 0.26f * f)
                if (shelf.progress && p.hasProgress) {
                    val inset = px(6f) * scale
                    rf3.set(l + inset, t + h - inset - px(2.5f), l + w - inset, t + h - inset)
                    fill.color = withAlpha(Color.WHITE, 0.25f * ca)
                    cv.drawRoundRect(rf3, px(1.25f), px(1.25f), fill)
                    rf3.right = rf3.left + (rf3.width()) * (p.position.toFloat() / p.duration).coerceIn(0.04f, 1f)
                    fill.color = withAlpha(Color.WHITE, 0.95f * ca)
                    cv.drawRoundRect(rf3, px(1.25f), px(1.25f), fill)
                }
                if (f > 0.01f) {
                    rf3.set(l, t, l + w, t + h)
                    stroke.strokeWidth = px(1.5f)
                    stroke.color = withAlpha(Color.WHITE, 0.55f * f * ca)
                    cv.drawRoundRect(rf3, r * scale, r * scale, stroke)
                }
            }
        }
    }

    /** Context line, title, then one line of detail, grouped just above the first row. */
    private fun heroText(cv: Canvas, context: String, title: String, line: String, alpha: Float, dy: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val x = px(MARGIN)
        val base = px(HERO_BASE) + dy
        // No text shadows (see softShadow); the backdrop's left scrim keeps the text legible.
        if (context.isNotEmpty()) {
            ctxP.alpha = (a * 0.62f).toInt()
            cv.drawText(context, x + px(1f), base - px(42f), ctxP)
        }
        titleP.alpha = a
        cv.drawText(fit(title, titleP, px(620f)), x, base, titleP)
        if (line.isNotEmpty()) {
            tagP.alpha = (a * 0.9f).toInt()
            cv.drawText(fit(line, tagP, px(620f)), x + px(1f), base + px(25f), tagP)
        }
    }

    /** Measures before ellipsizing, so the common case allocates nothing per frame. */
    private fun fit(text: String, paint: TextPaint, max: Float): String =
        if (paint.measureText(text) <= max) text else TextUtils.ellipsize(text, paint, max, TextUtils.TruncateAt.END).toString()

    /**
     * Strings that change at most once a minute, built once per minute instead of every frame
     * (the draw loop runs at 60 fps while anything moves).
     */
    private val texts = object {
        private var minute = -1L
        private var time = ""
        private var date = ""
        private var greeting = ""
        private var weather = ""
        private var weatherHere = ""
        private var weatherRange = ""

        private fun refresh() {
            val now = System.currentTimeMillis() / 60_000L
            if (now == minute) return
            minute = now
            val d = Date()
            time = (if (prefs.clock24) timeFmt24 else timeFmt12).format(d)
            date = dateFmt.format(d)
            greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good night"
            }
            // Fragments joined by " · ". The condition is always its own fragment in its own
            // case, never lowercased inside a sentence.
            val wx = host.weather()?.takeIf { prefs.weatherOn }
            val f = prefs.fahrenheit
            weatherRange = if (wx != null && wx.hasRange) "High ${wx.temp(f, wx.highC)} · Low ${wx.temp(f, wx.lowC)}" else ""
            weather = if (wx == null) "" else if (weatherRange.isEmpty()) wx.description else "${wx.description} · $weatherRange"
            weatherHere = if (wx == null) "" else "${prefs.place.substringBefore(',')} · ${wx.temp(f)} · ${wx.description}"
        }

        fun invalidate() { minute = -1L }
        fun time(): String { refresh(); return time }
        fun date(): String { refresh(); return date }
        fun greeting(): String { refresh(); return greeting }
        /** "Partly cloudy · High 11° · Low 2°" */
        fun weatherLine(): String { refresh(); return weather }
        /** "Toronto · 6° · Partly cloudy" */
        fun weatherHere(): String { refresh(); return weatherHere }
        /** "High 11° · Low 2°" */
        fun weatherRange(): String { refresh(); return weatherRange }
    }

    /** Call when settings or data behind the cached strings change. */
    fun textsChanged() {
        texts.invalidate()
        kick()
    }

    // ------------------------------------------------------------------ grid

    private fun drawGrid(cv: Canvas) {
        if (tiles.isEmpty()) return
        val dimByOverlay = 1f - 0.45f * max(sheetOpen.value, menuOpen.value * 0.6f)
        val dimByTop = 1f - 0.25f * max(max(topFocus.maxOf { it.value }, chipFocus.value), max(queueOpen.value, mediaOpen.value) * 1.2f)
        val sy = px(scroll.value)
        val h = height.toFloat()
        // Unfocused first, then the focused one on top so its shadow and lift overlap neighbours.
        var focusedTile: Tile? = null
        for ((i, tile) in tiles.withIndex()) {
            if (i == focusIndex && (tile.focus.value > 0.02f || moving)) { focusedTile = tile; continue }
            val top = tileTop(tile, i)
            if (top > h + px(20f) || top + px(TILE_H) < -px(40f)) continue
            drawTile(cv, tile, i, dimByOverlay * dimByTop)
        }
        focusedTile?.let { drawTile(cv, it, focusIndex, dimByOverlay) }
    }

    private fun drawTile(cv: Canvas, tile: Tile, index: Int, dim: Float) {
        val f = tile.focus.value
        val isFocused = index == focusIndex
        val appear = tile.appear.value.coerceIn(0f, 1f)
        if (appear <= 0.001f) return
        val tw = px(TILE_W)
        val th = px(TILE_H)
        val lifted = if (isFocused) lift.value else 0f
        val press = if (isFocused) pressS.value else 0f
        val scale = 1f + FOCUS_SCALE * f + 0.06f * lifted - 0.055f * press
        var cx = px(tile.x.value) + tw / 2f
        var cy = tileTop(tile, index) + th / 2f + px(18f) * (1f - appear)
        if (isFocused) {
            cx += px(nudgeX.value)
            cy += px(nudgeY.value)
        }

        // Rows scrolled up under the clock fade out; dimming never makes a tile see-through.
        val top = cy - th / 2f
        val alpha = appear * (top / px(120f) + 0.35f).coerceIn(0f, 1f)
        if (alpha < 0.01f) return
        val browsing = hero.value < 0.5f
        val bright = dim * (if (browsing && rowOf(index) < rowOf(focusIndex)) 0.55f else 1f) * (0.9f + 0.1f * max(f, if (browsing) 0f else 1f))
        val filter = darken(bright)

        cv.save()
        cv.translate(cx, cy)

        // Lifted for moving: a gentle wobble, the tvOS "jiggle".
        if (lifted > 0.01f) cv.rotate(sin(t * 18f + tile.jigglePhase) * 1.6f * lifted)

        // Shadow: grows and softens with focus.
        shadowBmp?.let { sb ->
            val sScale = scale * (1f + 0.04f * f)
            val sw = (tw + shadowPad * 2f) * sScale
            val sh = (th + shadowPad * 2f) * sScale
            val sdy = px(5f) + px(14f) * f + px(10f) * lifted
            rf.set(-sw / 2f, -sh / 2f + sdy, sw / 2f, sh / 2f + sdy)
            bmpPaint.alpha = ((0.32f + 0.4f * f) * alpha * 255).toInt()
            cv.drawBitmap(sb, null, rf, bmpPaint)
            bmpPaint.alpha = 255
        }

        // No tilt: focus is shown by lift, shadow and light, and a press never snaps the tile.
        cv.scale(scale, scale)

        rf.set(-tw / 2f, -th / 2f, tw / 2f, th / 2f)
        val r = px(RADIUS)
        val art = tile.app.art
        if (art != null && tile.shaderFor !== art) {
            tile.shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            tile.shaderFor = art
            // Artwork arriving after a placeholder was on screen fades in rather than popping.
            if (tile.placeholderShown) { tile.artFade.snap(0f); tile.artFade.target = 1f; kick() }
            tile.placeholderShown = false
        }
        val fade = if (art == null) 0f else tile.artFade.value.coerceIn(0f, 1f)
        if (fade < 1f) {
            tile.placeholderShown = tile.placeholderShown || art == null
            val pa = alpha * (1f - fade)
            fill.color = withAlpha(lerpColor(Color.BLACK, 0xFF2A2D36.toInt(), bright), alpha)
            cv.drawRoundRect(rf, r, r, fill)
            labelP.alpha = (pa * bright * 200).toInt()
            labelP.textSize = px(13f)
            cv.drawText(fit(tile.app.label, labelP, tw - px(16f)), 0f, px(5f), labelP)
        }
        if (art != null && fade > 0f) {
            val sh = tile.shader!!
            m.setScale(tw / art.width, th / art.height)
            m.postTranslate(-tw / 2f, -th / 2f)
            sh.setLocalMatrix(m)
            tilePaint.shader = sh
            tilePaint.alpha = (alpha * fade * 255).toInt()
            tilePaint.colorFilter = filter
            cv.drawRoundRect(rf, r, r, tilePaint)
            tilePaint.colorFilter = null
            tilePaint.shader = null
        }

        // Specular glare: a soft light across the top of the focused tile.
        glareShader?.let { gs ->
            if (f > 0.01f) {
                fill.shader = gs
                fill.alpha = (0.6f * f * alpha * 255).toInt()
                cv.drawRoundRect(rf, r, r, fill)
                fill.shader = null
                fill.alpha = 255
            }
        }
        // Hairline edge: defines tiles on dark art, brighter on top when focused (lit from above).
        stroke.strokeWidth = px(1f) / scale
        stroke.shader = edgeShader
        stroke.alpha = ((0.35f + 0.65f * f) * alpha * bright * 255).toInt()
        rf2.set(rf.left + stroke.strokeWidth / 2f, rf.top + stroke.strokeWidth / 2f, rf.right - stroke.strokeWidth / 2f, rf.bottom - stroke.strokeWidth / 2f)
        cv.drawRoundRect(rf2, r, r, stroke)
        stroke.shader = null
        stroke.alpha = 255
        cv.restore()

        // Name under the focused tile (or every tile, if the user wants).
        val showName = if (prefs.alwaysShowNames) 1f else f
        if (showName > 0.02f) {
            labelP.textSize = px(13.5f)
            labelP.alpha = (alpha * 255 * showName.coerceIn(0f, 1f)).toInt()
            val name = fit(tile.app.label, labelP, px(TILE_W + 16f))
            val ly = cy + th / 2f * scale + px(20f) + px(4f) * f
            cv.drawText(name, cx, ly, labelP)
        }
    }

    /**
     * Soft shadows as pre-blurred ALPHA_8 bitmaps, one per shape size, drawn as plain textured
     * quads. Never use Paint.setShadowLayer in the frame loop: on the SHIELD every new position
     * or scale of a shadowed shape left a new blurred mask in Skia's GPU cache (685 masks,
     * 171 MB, measured 2026-10-18).
     */
    private val shadowCache = HashMap<Long, Bitmap>()

    private fun softShadow(w: Float, h: Float, radius: Float, blur: Float): Bitmap {
        val wi = w.toInt().coerceAtLeast(1)
        val hi = h.toInt().coerceAtLeast(1)
        val key = (wi.toLong() shl 40) or (hi.toLong() shl 20) or ((radius.toInt().toLong() and 0x3FF) shl 10) or (blur.toInt().toLong() and 0x3FF)
        return shadowCache.getOrPut(key) {
            val pad = (blur * 2f).toInt()
            val b = Bitmap.createBitmap(wi + pad * 2, hi + pad * 2, Bitmap.Config.ALPHA_8)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL); color = Color.BLACK }
            Canvas(b).drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + wi).toFloat(), (pad + hi).toFloat()), radius, radius, p)
            b
        }
    }

    /** Draws a cached soft shadow under the rounded rect (l, t, r, b), offset down by [dy]. */
    private fun drawSoftShadow(cv: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, blur: Float, dy: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val bmp = softShadow(r - l, b - t, radius, blur)
        val pad = (blur * 2f).toInt().toFloat()
        rf2.set(l - pad, t - pad + dy, r + pad, b + pad + dy)
        bmpPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        cv.drawBitmap(bmp, null, rf2, bmpPaint)
        bmpPaint.alpha = 255
    }

    private val darkenCache = arrayOfNulls<LightingColorFilter>(33)

    /** A cached multiply filter: brightness 0..1 in 1/32 steps (null at full brightness). */
    private fun darken(bright: Float): LightingColorFilter? {
        val step = (bright.coerceIn(0f, 1f) * 32f).toInt()
        if (step >= 32) return null
        return darkenCache[step] ?: run {
            val v = (step * 255 / 32)
            LightingColorFilter(Color.rgb(v, v, v), 0).also { darkenCache[step] = it }
        }
    }

    /** On-screen top of a tile in pixels, including scroll. */
    private fun tileTop(tile: Tile, index: Int): Float = px(tile.y.value - scroll.value)

    fun focusedBounds(): Rect {
        val i = focusIndex
        val tw = px(TILE_W) * (1f + FOCUS_SCALE)
        val th = px(TILE_H) * (1f + FOCUS_SCALE)
        val cx = px(colX(i) + TILE_W / 2f)
        val cy = tiles.getOrNull(i)?.let { tileTop(it, i) + px(TILE_H) / 2f } ?: px(rowY(i) - scroll.value + TILE_H / 2f)
        return Rect((cx - tw / 2f).toInt(), (cy - th / 2f).toInt(), (cx + tw / 2f).toInt(), (cy + th / 2f).toInt())
    }

    // ------------------------------------------------------------------ top bar

    private fun topButtonCenter(i: Int): Float = px(W - MARGIN - 17f - (topButtons.size - 1 - i) * 46f)

    /**
     * The now-playing chip, ending at [right]: artwork, "Title · Artist" and a play or pause sign.
     * Focusable from the top bar (Left from Search); OK plays or pauses.
     */
    private fun drawChip(cv: Canvas, right: Float, cy: Float, topAlpha: Float) {
        val show = chipShow.value.coerceIn(0f, 1f)
        val np = nowPlaying()
        if (show < 0.01f || np == null) return
        val alpha = show * topAlpha
        val f = chipFocus.value.coerceIn(0f, 1f)
        itemP.textSize = px(13f)
        val text = fit(if (np.artist.isEmpty()) np.title else "${np.title} · ${np.artist}", itemP, px(190f))
        // As tall as the round Search and Control center buttons. The artwork is a circle inside the
        // pill's rounded end, the same distance from the edge all round.
        val h = px(34f)
        val ring = px(4f)
        val r = h / 2f - ring
        val w = h / 2f + r + px(10f) + itemP.measureText(text) + px(10f) + px(14f) + px(13f)
        val s = 1f + 0.06f * f
        val left = right - w
        cv.save()
        cv.scale(s, s, right - w / 2f, cy)
        // No shadow: its width follows the title, and a cached blur per width would pile up.
        rf3.set(left, cy - h / 2f, right, cy + h / 2f)
        fill.color = lerpColor(withAlpha(Color.WHITE, 0.14f * alpha), withAlpha(Color.WHITE, alpha), f)
        cv.drawRoundRect(rf3, h / 2f, h / 2f, fill)
        val acx = left + h / 2f
        val bmp = np.art ?: np.artUri?.let { host.content().art(it, forMusic = true) }
        if (bmp != null) {
            val key = "chip:${np.key}"
            val shader = artShaders[key]?.takeIf { it.first === bmp }?.second
                ?: BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { artShaders[key] = bmp to it }
            val k = 2f * r / min(bmp.width, bmp.height)
            m.setScale(k, k)
            m.postTranslate(acx - bmp.width * k / 2f, cy - bmp.height * k / 2f)
            shader.setLocalMatrix(m)
            artPaint.shader = shader
            artPaint.alpha = (alpha * 255).toInt()
            cv.drawCircle(acx, cy, r, artPaint)
            artPaint.shader = null
        } else {
            fill.color = withAlpha(Color.WHITE, 0.18f * alpha)
            cv.drawCircle(acx, cy, r, fill)
            Glyphs.draw(cv, "music", acx, cy, r * 1.1f, withAlpha(Color.WHITE, 0.8f * alpha))
        }
        val color = lerpColor(withAlpha(Color.WHITE, 0.92f * alpha), withAlpha(0xFF16181D.toInt(), alpha), f)
        itemP.color = color
        cv.drawText(text, acx + r + px(10f), cy + px(4.5f), itemP)
        itemP.color = Color.WHITE
        Glyphs.draw(cv, if (np.playing) "media-pause" else "media-play", right - px(19f), cy, px(14f), color)
        cv.restore()
        if (f > 0.02f && zone == Zone.TOP) {
            labelP.textSize = px(12.5f)
            labelP.alpha = (f * alpha * 255).toInt()
            cv.drawText(if (np.playing) "Pause" else "Play", right - w / 2f, cy + px(42f), labelP)
        }
    }

    private fun drawTopBar(cv: Canvas) {
        val y = px(46f)
        val alpha = 1f - sheetOpen.value
        if (alpha < 0.01f) return
        for ((i, name) in topButtons.withIndex()) {
            val f = topFocus[i].value
            val cx = topButtonCenter(i)
            val rad = px(17f) * (1f + 0.18f * f)
            fill.color = lerpColor(withAlpha(Color.WHITE, 0.14f * alpha), withAlpha(Color.WHITE, alpha), f)
            if (f > 0.02f) {
                // One cached circle shadow at full size, scaled with the button.
                val full = px(17f) * 1.18f
                val blur = px(8f)
                val bmp = softShadow(full * 2f, full * 2f, full, blur)
                val k = rad / full
                val half = (full + blur * 2f) * k
                val sdy = px(4f) * f
                rf2.set(cx - half, y - half + sdy, cx + half, y + half + sdy)
                bmpPaint.alpha = (0.4f * f * alpha * 255).toInt()
                cv.drawBitmap(bmp, null, rf2, bmpPaint)
                bmpPaint.alpha = 255
            }
            cv.drawCircle(cx, y, rad, fill)
            Glyphs.draw(cv, if (name == "search") "search" else "controls", cx, y, px(17f) * (1f + 0.12f * f), lerpColor(withAlpha(Color.WHITE, 0.9f * alpha), 0xFF16181D.toInt(), f))
        }
        // Clock and weather, to the left of the buttons.
        var right = topButtonCenter(0) - px(17f) - px(18f)
        timeP.textSize = px(19f)
        timeP.alpha = (alpha * 255).toInt()
        val time = texts.time()
        cv.drawText(time, right, y + px(7f), timeP)
        right -= timeP.measureText(time) + px(16f)
        val wx = host.weather()
        if (prefs.weatherOn && wx != null) {
            timeP.alpha = (alpha * 230).toInt()
            val temp = wx.temp(prefs.fahrenheit)
            cv.drawText(temp, right, y + px(7f), timeP)
            right -= timeP.measureText(temp) + px(14f)
            Glyphs.draw(cv, wx.glyph, right, y, px(20f), withAlpha(Color.WHITE, 0.92f * alpha))
            right -= px(10f)
        }
        drawChip(cv, right - px(20f), y, alpha)

        // Label for the focused top button.
        val fMax = topFocus.maxOf { it.value }
        if (fMax > 0.02f && zone == Zone.TOP) {
            val label = if (topIndex == 0) "Search" else "Control center"
            labelP.textSize = px(12.5f)
            labelP.alpha = (fMax * alpha * 255).toInt()
            cv.drawText(label, topButtonCenter(topIndex), y + px(42f), labelP)
        }
    }

    // ------------------------------------------------------------------ toast

    private fun drawToast(cv: Canvas) {
        val text = toastText ?: return
        val a = toastS.value.coerceIn(0f, 1f)
        if (a < 0.01f) return
        itemP.textSize = px(14f)
        val tw = itemP.measureText(text)
        val cx = width / 2f
        // Top centre, level with the clock, where it never covers a tile.
        val y = px(46f) - px(14f) * (1f - a)
        rf.set(cx - tw / 2f - px(18f), y - px(18f), cx + tw / 2f + px(18f), y + px(18f))
        fill.color = withAlpha(0xFF1C1E24.toInt(), 0.92f * a)
        cv.drawRoundRect(rf, px(18f), px(18f), fill)
        itemP.alpha = (a * 255).toInt()
        itemP.textAlign = Paint.Align.CENTER
        cv.drawText(text, cx, y + px(5f), itemP)
        itemP.textAlign = Paint.Align.LEFT
        itemP.alpha = 255
    }

    // ------------------------------------------------------------------ menu

    private fun openMenu() {
        val tile = tiles.getOrNull(focusIndex) ?: return
        val app = tile.app
        val items = ArrayList<SheetItem>()
        items += SheetItem("move", "Move") { closeMenu(); startMove() }
        items += SheetItem("eye-off", "Hide from home") { closeMenu(); host.hide(app) }
        if (app.component != null) items += SheetItem("info", "App info") { closeMenu(); host.appInfo(app) }
        menuItems = items
        menuFor = tile
        menuFocus = 0
        menuPill.snap(0f)
        menuOpen.target = 1f
        kick()
    }

    private fun closeMenu() {
        menuOpen.target = 0f
        kick()
    }

    private fun menuOpen() = menuOpen.target > 0f && menuItems.isNotEmpty()

    private fun drawMenu(cv: Canvas) {
        val a = menuOpen.value.coerceIn(0f, 1f)
        if (a < 0.01f || menuItems.isEmpty()) return
        val tile = menuFor ?: return
        val itemH = px(42f)
        val mw = px(230f)
        val mh = itemH * menuItems.size + px(12f)
        val tileCx = px(tile.x.value + TILE_W / 2f)
        val tileTopPx = tileTop(tile, tiles.indexOf(tile))
        val tileBottom = tileTopPx + px(TILE_H) + px(TILE_H) * FOCUS_SCALE / 2f
        val left = (tileCx - mw / 2f).coerceIn(px(MARGIN), width - px(MARGIN) - mw)
        var top = tileBottom + px(14f)
        if (top + mh > height - px(24f)) top = tileTopPx - px(TILE_H) * FOCUS_SCALE / 2f - px(14f) - mh
        val s = 0.94f + 0.06f * a
        cv.save()
        cv.scale(s, s, tileCx, top)
        drawSoftShadow(cv, left, top, left + mw, top + mh, px(16f), px(12f), px(10f), 0.5f * a)
        rf.set(left, top, left + mw, top + mh)
        fill.color = withAlpha(0xFF23252C.toInt(), 0.94f * a)
        cv.drawRoundRect(rf, px(16f), px(16f), fill)
        stroke.strokeWidth = px(1f)
        stroke.color = withAlpha(Color.WHITE, 0.08f * a)
        cv.drawRoundRect(rf, px(16f), px(16f), stroke)

        menuPill.target = menuFocus.toFloat()
        val py = top + px(6f) + itemH * menuPill.value
        rf2.set(left + px(6f), py, left + mw - px(6f), py + itemH)
        fill.color = withAlpha(Color.WHITE, a)
        cv.drawRoundRect(rf2, px(11f), px(11f), fill)
        itemP.textSize = px(15f)
        for ((i, item) in menuItems.withIndex()) {
            val iy = top + px(6f) + itemH * i
            val focusAmt = (1f - abs(menuPill.value - i)).coerceIn(0f, 1f)
            val color = lerpColor(withAlpha(Color.WHITE, a), withAlpha(0xFF15171C.toInt(), a), focusAmt)
            item.glyph?.let { Glyphs.draw(cv, it, left + px(30f), iy + itemH / 2f, px(17f), color) }
            itemP.color = color
            cv.drawText(item.label, left + px(52f), iy + itemH / 2f + px(5.5f), itemP)
        }
        itemP.color = Color.WHITE
        cv.restore()
    }

    // ------------------------------------------------------------------ side sheet

    private fun controlCenter() = SheetPage("control", null, header = true) {
        listOf(
            // These open over Telum (search and notifications are see-through overlays), so the
            // sheet goes at once instead of animating out underneath them.
            SheetItem("mic", "Search") { closeSheetNow(); host.system("search") },
            SheetItem("bell", "Notifications") { closeSheetNow(); host.system("notifications") },
            SheetItem("moon", "Screensaver") { closeSheetNow(); host.system("screensaver") },
            SheetItem("wifi", "Network & internet") { host.system("wifi") },
            SheetItem("display", "Display & sound") { host.system("display") },
            SheetItem("remote", "Remotes & accessories") { host.system("remotes") },
            SheetItem("grid", "Apps") { host.system("apps") },
            SheetItem("sliders", "Telum", chevron = true) { pushPage(telumPage()) },
            SheetItem("controls", "All settings") { host.system("settings") },
        )
    }

    private fun telumPage(): SheetPage {
        // Read once when the page opens, not on every rebuild or frame: getMemoryInfo walks /proc.
        val memory = host.memoryLine() // refreshes in the background; the row reads the latest
        val stockHome = host.stockHomeEnabled()
        return SheetPage("telum", "Telum") { telumItems(memory, stockHome) }
    }

    private fun telumItems(memory: String, stockHome: Boolean): List<SheetItem> =
        listOf(
            SheetItem("move", "Rearrange apps") { closeSheet(); zone = Zone.GRID; startMove() },
            SheetItem(
                "image", "Wallpaper",
                value = { wallpaperName(prefs.wallpaper) },
                choice = { d -> prefs.wallpaper = cycle(WALLPAPERS, prefs.wallpaper, d); host.settingsChanged("wallpaper") },
            ),
            SheetItem(
                "type", "Typeface",
                value = { Fonts.name(prefs.typeface) },
                choice = { d -> prefs.typeface = cycle(Fonts.CHOICES, prefs.typeface, d); host.settingsChanged("typeface") },
            ),
            // Rows other apps publish (Stremio's Continue Watching). Turning it on asks Android first.
            SheetItem("play", "Show what you're watching", value = { if (host.content().enabled) "On" else "Off" },
                choice = { host.setWatching(!host.content().enabled) }),
            SheetItem("rows", "Rows from apps", chevron = true, enabled = host.content().enabled) { pushPage(rowsPage()) },
            // What other apps are playing, with play, pause and skip. Needs notification access.
            SheetItem("music", "Music controls", value = { if (host.media().enabled) "On" else "Off" },
                choice = { host.setMusicControls(!host.media().enabled) }),
            // Off by default: finding a photo means sending the artist's name to TheAudioDB.
            SheetItem("image", "Artist photos", value = { if (prefs.artistPhotos) "On" else "Off" }, enabled = host.media().enabled,
                choice = {
                    prefs.artistPhotos = !prefs.artistPhotos
                    if (prefs.artistPhotos) toast("Artist names are looked up on TheAudioDB to find photos")
                    heroShown?.let { if (it.media != null) it.backdrop = 0 }
                    kick()
                }),
            SheetItem("motion", "Motion", value = { if (prefs.motion) "On" else "Reduced" },
                choice = { prefs.motion = !prefs.motion; host.settingsChanged("motion") }),
            SheetItem("label", "App names", value = { if (prefs.alwaysShowNames) "Always" else "When focused" },
                choice = { prefs.alwaysShowNames = !prefs.alwaysShowNames; host.settingsChanged("names") }),
            SheetItem("clock", "Clock", value = { if (prefs.clock24) "24-hour" else "12-hour" },
                choice = { prefs.clock24 = !prefs.clock24; host.settingsChanged("clock") }),
            SheetItem("partly", "Weather", value = { if (prefs.weatherOn) "On" else "Off" },
                choice = { prefs.weatherOn = !prefs.weatherOn; host.settingsChanged("weather") }),
            SheetItem("thermo", "Units", value = { if (prefs.fahrenheit) "°F" else "°C" },
                choice = { prefs.fahrenheit = !prefs.fahrenheit; host.settingsChanged("units") }),
            SheetItem("pin", "Location", value = { prefs.place.substringBefore(',').ifBlank { "Choose a city" } }, chevron = true) { host.editPlace() },
            SheetItem("eye", "Hidden apps", value = { prefs.hidden.size.takeIf { it > 0 }?.toString() ?: "None" }, chevron = true) { pushPage(hiddenPage()) },
            if (stockHome) SheetItem("home", "Open Google TV home") { closeSheet(); host.system("stock-home") }
            else SheetItem("home", "Google TV home", value = { "Turned off" }, enabled = false),
            SheetItem("info", "Credits", chevron = true) { host.system("credits") },
            SheetItem("info", "About Telum", value = { host.memoryText() }, enabled = false),
        )

    private fun hiddenPage(): SheetPage = SheetPage("hidden", "Hidden apps") {
        val hidden = prefs.hidden
        val apps = host.allApps().filter { it.key in hidden }
        if (apps.isEmpty()) listOf(SheetItem(null, "No hidden apps", enabled = false))
        else apps.map { app -> SheetItem("eye-off", app.label, value = { "Show" }) { host.unhide(app.key) } }
    }

    /**
     * Which row each app shows above its tile. "Automatic" is a row with viewing progress (on the
     * SHIELD, Stremio's Continue Watching); any other row, such as SmartTube's History, is opt-in.
     */
    private fun rowsPage(): SheetPage = SheetPage("rows", "Rows from apps") {
        val shelves = host.content().shelves
        val labels = host.allApps().associate { it.pkg to it.label }
        val pkgs = shelves.keys.filter { it in labels }.sortedBy { labels[it]?.lowercase() }
        if (pkgs.isEmpty()) listOf(SheetItem(null, "No app has published a row yet", enabled = false))
        else pkgs.map { pkg ->
            val list = shelves[pkg].orEmpty()
            val auto = list.any { it.progress }
            val options = (if (auto) listOf("auto") else emptyList()) + "off" + list.map { it.name }.distinct()
            fun current() = prefs.rowChoice(pkg) ?: if (auto) "auto" else "off"
            SheetItem(
                null, labels[pkg]!!,
                value = { when (val c = current()) { "auto" -> "Automatic"; "off" -> "Off"; else -> c } },
                choice = { d ->
                    val next = cycle(options, current(), d)
                    // Defaults are stored as nothing, so a new row with progress still appears.
                    prefs.setRowChoice(pkg, if (next == "auto" || (next == "off" && !auto)) null else next)
                    host.settingsChanged("rows")
                },
            )
        }
    }

    private val WALLPAPERS = listOf("dynamic", "aurora", "ocean", "sunset", "forest", "graphite")
    private fun wallpaperName(id: String) = when (id) {
        "dynamic" -> "Follows app"
        "aurora" -> "Aurora"
        "ocean" -> "Ocean"
        "sunset" -> "Sunset"
        "forest" -> "Forest"
        "graphite" -> "Graphite"
        else -> id
    }

    private fun cycle(list: List<String>, cur: String, d: Int): String {
        val i = list.indexOf(cur).coerceAtLeast(0)
        return list[(i + d + list.size) % list.size]
    }

    fun openControlCenter() {
        closeMenu()
        pages.clear()
        pages += controlCenter()
        sheetPill.snap(0f)
        pageSwap.snap(1f)
        sheetOpen.target = 1f
        kick()
    }

    private fun pushPage(p: SheetPage) {
        pages += p
        pageDir = 1
        pageSwap.snap(0f); pageSwap.target = 1f
        sheetPill.snap(0f)
        kick()
    }

    private fun popPage() {
        if (pages.size <= 1) { closeSheet(); return }
        pages.removeAt(pages.size - 1)
        pageDir = -1
        pageSwap.snap(0f); pageSwap.target = 1f
        sheetPill.snap(pages.last().focus.toFloat())
        pages.last().rebuild()
        kick()
    }

    fun closeSheet() {
        sheetOpen.target = 0f
        kick()
    }

    /** Closes the sheet without animating, before something opens over Telum. */
    private fun closeSheetNow() {
        sheetOpen.snap(0f)
        pages.clear()
        kick()
    }

    private fun sheetOpen() = sheetOpen.target > 0f && pages.isNotEmpty()

    private fun drawSheet(cv: Canvas) {
        val a = sheetOpen.value
        if (a < 0.005f || pages.isEmpty()) return
        val page = pages.last()
        val w = px(372f)
        val left = width - px(24f) - w + px(420f) * (1f - a)
        val top = px(24f)
        val bottom = height - px(24f)

        // Dim the home screen behind.
        fill.color = withAlpha(Color.BLACK, 0.35f * a)
        cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        drawSoftShadow(cv, left, top, left + w, bottom, px(26f), px(15f), px(10f), 0.5f)
        rf.set(left, top, left + w, bottom)
        val mood = currentMood()
        fill.color = withAlpha(lerpColor(0xFF1A1C22.toInt(), mood.base, 0.4f), 0.94f)
        cv.drawRoundRect(rf, px(26f), px(26f), fill)
        // Soft colour wash at the top of the sheet, echoing the backdrop.
        // Built in sheet-local space and only rebuilt when the colour changes; sliding just moves it.
        val washColor = withAlpha(mood.a, 0.28f)
        val wash = sheetWash?.takeIf { sheetWashColor == washColor }
            ?: RadialGradient(w * 0.3f, 0f, w * 0.9f, washColor, 0x00000000, Shader.TileMode.CLAMP)
                .also { sheetWash = it; sheetWashColor = washColor }
        m.setTranslate(left, top)
        wash.setLocalMatrix(m)
        fill.shader = wash
        cv.drawRoundRect(rf, px(26f), px(26f), fill)
        fill.shader = null
        stroke.strokeWidth = px(1f)
        stroke.color = 0x14FFFFFF
        cv.drawRoundRect(rf, px(26f), px(26f), stroke)

        cv.save()
        cv.clipRect(rf)
        val slide = (1f - pageSwap.value) * px(40f) * pageDir
        val pa = pageSwap.value
        cv.translate(slide, 0f)

        val pad = px(24f)
        var y = top + px(30f)
        if (page.header) {
            bigTimeP.textSize = px(54f)
            bigTimeP.alpha = (255 * pa).toInt()
            cv.drawText(texts.time(), left + pad, y + px(44f), bigTimeP)
            y += px(70f)
            tagP.textSize = px(15f)
            tagP.alpha = (220 * pa).toInt()
            cv.drawText(texts.date(), left + pad, y, tagP)
            y += px(14f)
            val wx = host.weather()
            if (prefs.weatherOn && wx != null) {
                y += px(16f)
                Glyphs.draw(cv, wx.glyph, left + pad + px(11f), y, px(22f), withAlpha(Color.WHITE, 0.9f * pa))
                metaP.textSize = px(13.5f)
                metaP.alpha = (200 * pa).toInt()
                cv.drawText(fit(texts.weatherHere(), metaP, w - pad * 2f - px(32f)), left + pad + px(32f), y + px(5f), metaP)
                metaP.alpha = (130 * pa).toInt()
                cv.drawText(texts.weatherRange(), left + pad + px(32f), y + px(22f), metaP)
                y += px(26f)
            }
            y += px(18f)
            tagP.textSize = px(16f)
        } else {
            Glyphs.draw(cv, "back", left + pad + px(6f), y + px(12f), px(18f), withAlpha(Color.WHITE, 0.6f * pa))
            titleP.textSize = px(26f)
            titleP.alpha = (255 * pa).toInt()
            cv.drawText(page.title ?: "", left + pad + px(24f), y + px(21f), titleP)
            titleP.textSize = px(40f)
            y += px(52f)
        }

        val itemH = px(44f)
        val listTop = y
        val listH = bottom - px(14f) - listTop
        // Keep the focused row visible.
        val focusTop = page.focus * itemH
        val sc = page.scroll.target
        if (focusTop - sc > listH - itemH) page.scroll.target = focusTop - listH + itemH
        if (focusTop - sc < 0) page.scroll.target = focusTop
        cv.save()
        cv.clipRect(left, listTop - px(4f), left + w, bottom - px(10f))
        cv.translate(0f, -page.scroll.value)

        sheetPill.target = page.focus.toFloat()
        val pillY = listTop + itemH * sheetPill.value
        drawSoftShadow(cv, left + px(12f), pillY, left + w - px(12f), pillY + itemH, px(13f), px(5f), px(3f), 0.25f * pa)
        rf2.set(left + px(12f), pillY, left + w - px(12f), pillY + itemH)
        fill.color = withAlpha(Color.WHITE, pa)
        cv.drawRoundRect(rf2, px(13f), px(13f), fill)

        itemP.textSize = px(15.5f)
        valueP.textSize = px(14f)
        for ((i, item) in page.items.withIndex()) {
            val iy = listTop + itemH * i
            if (iy - page.scroll.value > bottom || iy + itemH - page.scroll.value < listTop - itemH) continue
            val fa = (1f - abs(sheetPill.value - i)).coerceIn(0f, 1f)
            val base = if (item.enabled) 1f else 0.5f
            val color = lerpColor(withAlpha(Color.WHITE, pa * base), withAlpha(0xFF14161B.toInt(), pa), fa)
            var tx = left + pad
            if (item.glyph != null) {
                Glyphs.draw(cv, item.glyph, tx + px(10f), iy + itemH / 2f, px(18f), color)
                tx += px(32f)
            }
            itemP.color = color
            cv.drawText(item.label, tx, iy + itemH / 2f + px(5.5f), itemP)
            var vr = left + w - pad
            if (item.chevron) {
                Glyphs.draw(cv, "chevron", vr - px(6f), iy + itemH / 2f, px(14f), withAlpha(color, 0.6f * pa + 0.2f))
                vr -= px(20f)
            }
            item.value?.let { v ->
                val text = v()
                valueP.color = lerpColor(withAlpha(Color.WHITE, 0.55f * pa), withAlpha(0xFF14161B.toInt(), 0.6f * pa), fa)
                val shown = if (item.choice != null && fa > 0.5f) "‹  $text  ›" else text
                cv.drawText(fit(shown, valueP, px(150f)), vr, iy + itemH / 2f + px(5f), valueP)
            }
        }
        itemP.color = Color.WHITE
        cv.restore()
        cv.restore()
    }

    // ------------------------------------------------------------------ input

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    /** True only while a press that can become a long press (OK on a grid tile) is held. */
    private var longPressArmed = false

    private val longPress = Runnable {
        if (!okDown || !longPressArmed) return@Runnable
        longPressArmed = false
        longPressFired = true
        pressS.target = 0f
        if (zone == Zone.GRID && !moving && !menuOpen() && !sheetOpen()) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            openMenu()
        }
        kick()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val restored = musicInteraction()
        if (event.repeatCount == 0) idleWakeKey = null
        lastKeyAt = SystemClock.uptimeMillis()
        traceKicks = 0 // a key press explains the next frames; trace only kicks from elsewhere
        kick()
        // The first navigation press reveals the apps without launching the previously focused
        // one. Media and volume keys still reach Android's normal playback handling.
        if (restored && (isOk(keyCode) || keyCode in arrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU))) idleWakeKey = keyCode
        if (idleWakeKey == keyCode) return true
        if (isOk(keyCode)) {
            if (event.repeatCount == 0) {
                okDown = true
                longPressFired = false
                longPressArmed = false
                if (zone == Zone.GRID && !menuOpen() && !sheetOpen() && !moving) {
                    pressS.target = 1f
                    longPressArmed = true
                    postDelayed(longPress, LONG_PRESS_MS)
                }
            } else if (event.isLongPress && okDown && longPressArmed) {
                // The system's own long-press flag (first key repeat) may arrive before our timer.
                removeCallbacks(longPress)
                longPress.run()
            }
            return true
        }
        // Holding Back or Menu must not toggle panels open and shut with every key repeat.
        if (event.repeatCount > 0 && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
            return true
        }
        when {
            menuOpen() -> return menuKey(keyCode)
            sheetOpen() -> return sheetKey(keyCode)
            moving -> return moveKey(keyCode)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { navigate(-1, 0); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { navigate(1, 0); true }
            KeyEvent.KEYCODE_DPAD_UP -> { navigate(0, -1); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { navigate(0, 1); true }
            KeyEvent.KEYCODE_MENU -> { if (zone == Zone.GRID) openMenu() else openControlCenter(); true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> { back(); true }
            else -> false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        musicInteraction()
        if (idleWakeKey == keyCode) { idleWakeKey = null; return true }
        if (isOk(keyCode)) {
            removeCallbacks(longPress)
            val wasDown = okDown
            okDown = false
            pressS.target = 0f
            kick()
            if (!wasDown || longPressFired || event.isCanceled) return true
            activate()
            return true
        }
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU || super.onKeyUp(keyCode, event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) idleWakeTouch = musicInteraction()
        if (idleWakeTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) idleWakeTouch = false
            kick()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun activate() {
        when {
            menuOpen() -> menuItems.getOrNull(menuFocus)?.action?.invoke()
            sheetOpen() -> {
                val item = pages.last().items.getOrNull(pages.last().focus) ?: return
                if (!item.enabled) return
                if (item.choice != null) { item.choice.invoke(1); pages.last().rebuild() }
                else item.action?.invoke()
            }
            moving -> finishMove()
            zone == Zone.TOP && chipFocused -> { playSoundEffect(SoundEffectConstants.CLICK); host.media().playPause() }
            zone == Zone.TOP -> if (topIndex == 0) host.system("search") else openControlCenter()
            zone == Zone.MEDIA -> {
                playSoundEffect(SoundEffectConstants.CLICK)
                when (mediaIndex) {
                    0 -> host.media().previous()
                    2 -> host.media().next()
                    else -> host.media().playPause()
                }
            }
            zone == Zone.QUEUE -> {
                val app = queueApp ?: return
                val p = queueShelf?.items?.getOrNull(queueIndex) ?: return
                playSoundEffect(SoundEffectConstants.CLICK)
                prefs.markOpened(app.key)
                host.openProgramme(app.pkg, p)
            }
            else -> {
                val tile = tiles.getOrNull(focusIndex) ?: return
                playSoundEffect(SoundEffectConstants.CLICK)
                prefs.markOpened(tile.app.key)
                texts.invalidate()
                host.launch(tile.app, focusedBounds())
            }
        }
        kick()
    }

    private fun back() {
        when {
            zone == Zone.TOP -> { chipFocused = false; zone = Zone.GRID }
            zone == Zone.QUEUE -> closeQueue()
            zone == Zone.MEDIA -> closeMedia()
            rowOf(focusIndex) > 0 -> moveFocusTo(focusIndex % COLS)
            focusIndex != 0 -> moveFocusTo(0)
            else -> bump(-1, 0)
        }
    }

    private fun navigate(dx: Int, dy: Int) {
        if (zone == Zone.TOP) {
            when {
                dy > 0 -> { chipFocused = false; zone = Zone.GRID }
                // The now-playing chip sits left of Search.
                dx < 0 && topIndex == 0 && !chipFocused && chipWanted() -> { chipFocused = true; playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT) }
                dx > 0 && chipFocused -> { chipFocused = false; topIndex = 0; playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT) }
                dx != 0 && !chipFocused -> {
                    val n = topIndex + dx
                    if (n in topButtons.indices) {
                        topIndex = n
                        playSoundEffect(if (dx < 0) SoundEffectConstants.NAVIGATION_LEFT else SoundEffectConstants.NAVIGATION_RIGHT)
                    }
                }
            }
            kick()
            return
        }
        if (zone == Zone.MEDIA) {
            when {
                dx != 0 -> {
                    val n = mediaIndex + dx
                    if (n in 0..2) { mediaIndex = n; playSoundEffect(navSound(dx, 0)) }
                }
                dy > 0 -> closeMedia()
                dy < 0 -> { zone = Zone.TOP; topIndex = 0 }
            }
            kick()
            return
        }
        if (zone == Zone.QUEUE) {
            val shelf = queueShelf ?: run { closeQueue(); return }
            when {
                dx != 0 -> {
                    val n = queueIndex + dx
                    if (n in shelf.items.indices) { queueIndex = n; playSoundEffect(navSound(dx, 0)) }
                    else nudgeX.velocity += dx * 60f
                }
                dy > 0 -> closeQueue()
                dy < 0 -> { zone = Zone.TOP; topIndex = 0 }
            }
            kick()
            return
        }
        if (tiles.isEmpty()) return
        val col = focusIndex % COLS
        val row = rowOf(focusIndex)
        var target = focusIndex
        if (dx != 0) {
            val n = focusIndex + dx
            // Left/right stays within the row, as on tvOS.
            if (n >= 0 && n < tiles.size && rowOf(n) == row) target = n
        } else if (dy < 0) {
            if (row == 0) {
                // Up opens the playing app's controls, or the app's row of programmes, or the top bar.
                val app = tiles.getOrNull(focusIndex)?.app
                val np = nowPlaying()
                val shelf = contentFor(app)
                when {
                    app != null && np != null && np.pkg == app.pkg -> openMedia(app)
                    app != null && shelf != null -> openQueue(app, shelf)
                    else -> { zone = Zone.TOP; topIndex = 0 }
                }
                return
            }
            target = focusIndex - COLS
        } else if (dy > 0) {
            if (row + 1 < rows()) target = min(focusIndex + COLS, tiles.size - 1)
        }
        if (target == focusIndex) { bump(dx, dy); return }
        moveFocusTo(target, dx, dy)
        if (col != target % COLS || dy != 0) Unit
    }

    private fun navSound(dx: Int, dy: Int) = when {
        dx < 0 -> SoundEffectConstants.NAVIGATION_LEFT
        dx > 0 -> SoundEffectConstants.NAVIGATION_RIGHT
        dy < 0 -> SoundEffectConstants.NAVIGATION_UP
        else -> SoundEffectConstants.NAVIGATION_DOWN
    }

    private fun moveFocusTo(target: Int, dx: Int = 0, dy: Int = 0) {
        if (target !in tiles.indices) return
        focusIndex = target
        // System navigation sounds: only audible when the user has Sound effects on.
        playSoundEffect(navSound(dx, dy))
        // Focus responds on this frame; nothing is snapped, and the backdrop waits for focus to rest.
        focusChanged(tiles[target].app)
        kick()
    }

    /** Hitting an edge: the focused tile nudges and springs back, so the press is never ignored. */
    private fun bump(dx: Int, dy: Int) {
        nudgeX.velocity += dx * 100f
        nudgeY.velocity += dy * 90f
        kick()
    }

    private fun menuKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (menuFocus > 0) { menuFocus--; playSoundEffect(navSound(0, -1)) } else menuPill.velocity -= 6f
            KeyEvent.KEYCODE_DPAD_DOWN -> if (menuFocus < menuItems.size - 1) { menuFocus++; playSoundEffect(navSound(0, 1)) } else menuPill.velocity += 6f
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU -> closeMenu()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {}
            else -> return false
        }
        kick()
        return true
    }

    private fun sheetKey(code: Int): Boolean {
        val page = pages.last()
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (page.focus > 0) { page.focus--; playSoundEffect(navSound(0, -1)) } else sheetPill.velocity -= 6f
            KeyEvent.KEYCODE_DPAD_DOWN -> if (page.focus < page.items.size - 1) { page.focus++; playSoundEffect(navSound(0, 1)) } else sheetPill.velocity += 6f
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val item = page.items.getOrNull(page.focus)
                val d = if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                if (item?.choice != null) { item.choice.invoke(d); page.rebuild() }
                else if (d < 0 && pages.size > 1) popPage()
                else if (d < 0) closeSheet()
                else if (item?.chevron == true) item.action?.invoke()
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> popPage()
            KeyEvent.KEYCODE_MENU -> closeSheet()
            else -> return false
        }
        kick()
        return true
    }

    // ------------------------------------------------------------------ moving apps

    private fun startMove() {
        if (tiles.isEmpty()) return
        zone = Zone.GRID
        moving = true
        toast("Move with the arrows, then press OK")
        kick()
    }

    private fun moveKey(code: Int): Boolean {
        val d = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> -COLS
            KeyEvent.KEYCODE_DPAD_DOWN -> COLS
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU -> { finishMove(); return true }
            else -> return false
        }
        val n = (focusIndex + d).coerceIn(0, tiles.size - 1)
        if (n == focusIndex) { bump(if (abs(d) == 1) d else 0, if (abs(d) == 1) 0 else d / COLS); return true }
        val list = tiles.toMutableList()
        val t0 = list.removeAt(focusIndex)
        list.add(n, t0)
        tiles = list
        focusIndex = n
        tiles.forEachIndexed { i, tile -> tile.x.target = colX(i); tile.y.target = rowY(i) }
        kick()
        return true
    }

    private fun finishMove() {
        moving = false
        host.saveOrder(tiles.map { it.app })
        toastS.target = 0f
        tiles.getOrNull(focusIndex)?.let { focusChanged(it.app) }
        // Apply any app-list change that arrived mid-move (an install or update), keeping the
        // order just saved.
        if (pendingApps != null) {
            pendingApps = null
            host.reloadApps()
        }
        kick()
    }
}
