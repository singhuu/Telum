package dev.telum.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Native focusable controls keep setup usable with a remote, touch and accessibility services. */
@SuppressLint("ViewConstructor") // Created by MainActivity with its settings and callbacks, never from XML.
class OnboardingView(
    context: Context,
    private val prefs: Prefs,
    private val host: Host,
    showIntro: Boolean = true,
) : LinearLayout(context) {
    interface Host {
        fun musicReady(): Boolean
        fun watchingReady(): Boolean
        fun connectMusic()
        fun connectWatching()
        fun chooseCity()
        fun retryApps()
        /** The steps to notification access on this device, for the screen Telum opens. */
        fun musicSteps(): List<String>
        fun previewActivities(result: (Map<String, List<Shelf>>) -> Unit)
        fun finish(choices: SetupChoices, selection: SetupApps)
        fun exit()
    }

    private companion object {
        /** When the title's button appears. The title's own sequence runs a second longer. */
        const val TITLE_ACTION_MS = 4000L
        val BACKGROUNDS = listOf("dynamic", "aurora", "ocean", "sunset", "forest", "graphite")
    }

    private val ink = Color.rgb(244, 246, 252)
    private val muted = Color.rgb(168, 181, 201)
    private val accent = Color.rgb(164, 228, 211)
    private var choices = prefs.setupDraft()
    private var step = prefs.setupStep
    private val replay = prefs.onboardingComplete
    private var scroller: ScrollView? = null
    private val controls = mutableMapOf<String, View>()
    /** Rows of the settings lists, in order. They route Up and Down themselves. */
    private val options = mutableListOf<View>()
    private var sideTitle: TextView? = null
    private var sideText: TextView? = null
    private var entries = emptyList<AppEntry>()
    private var inventoryLoaded = false
    private var inventoryChanged = false
    /** Final touches changed during a fade; it renders again when the fade ends. */
    private var connectionsChanged = false
    private var picker: SetupAppsView? = null
    private var selection = prefs.setupApps()
    private var transitioning = false
    private var titling = false
    private var opening = false
    private var titleAction: View? = null
    private var connectionsFrom = 1
    private var shelves: Map<String, List<Shelf>>? = null
    private var previewing = false
    // Declared before init, which posts it for the opening title.
    private val revealAction = Runnable {
        val action = titleAction ?: return@Runnable
        action.isFocusable = true
        action.requestFocus()
        action.animate().alpha(1f).translationY(0f).setDuration(if (choices.motion) 600L else 150L).start()
    }

    init {
        orientation = VERTICAL
        setPadding(dp(40), dp(22), dp(40), dp(18))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(19, 36, 48), Color.rgb(12, 17, 28), Color.rgb(33, 25, 44)))
        if (showIntro) {
            alpha = 0f
            title(opening = true)
            post { animate().alpha(1f).setDuration(if (choices.motion) 900L else 300L).start() }
        } else render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float, color: Int = ink, weight: Int = 500) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Fonts.face(context.assets, choices.typeface, weight)
        includeFontPadding = false
    }

    private fun panel(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(2), stroke)
    }

    private fun column() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(parent: LinearLayout, height: Int) = parent.addView(View(context), LayoutParams(1, dp(height)))

    private fun interactive(view: View, key: String, selected: Boolean = false, action: () -> Unit) {
        controls[key] = view
        view.id = View.generateViewId()
        view.isFocusable = true
        view.isClickable = true
        view.setOnClickListener { if (!transitioning && (!titling || key == "title")) action() }
        fun paint(focused: Boolean) {
            view.background = panel(if (focused) Color.rgb(42, 62, 73) else Color.rgb(25, 33, 46),
                if (focused) ink else if (selected) accent else Color.rgb(52, 62, 78))
        }
        paint(false)
        view.setOnFocusChangeListener { _, focused -> paint(focused) }
    }

    /** One line, as wide as its words: "Continue with Recommended" must never wrap. */
    private fun button(title: String, key: String, action: () -> Unit) = label(title, 14f, ink, 700).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        minWidth = dp(120)
        setPadding(dp(24), 0, dp(24), 0)
        interactive(this, key, action = action)
    }

    private fun fadeTo(out: Long = 220L, back: Long = 320L, after: () -> Unit) {
        if (transitioning) return
        transitioning = true
        animate().cancel()
        animate().alpha(0f).setDuration(if (choices.motion) out else 70L).withEndAction {
            after()
            if (!isAttachedToWindow) return@withEndAction
            animate().alpha(1f).setDuration(if (choices.motion) back else 80L)
                .withEndAction {
                    transitioning = false
                    if (titling) return@withEndAction
                    if (step == 0 && inventoryChanged) render()
                    else if (step == 3 && connectionsChanged) render(focusedKey())
                }.start()
        }.start()
    }

    // ------------------------------------------------------------------ titles

    /** The opening and closing screens. Their button appears after [TITLE_ACTION_MS]. */
    private fun title(opening: Boolean) {
        titling = true
        this.opening = opening
        removeCallbacks(revealAction)
        clearPage()
        gravity = Gravity.CENTER
        val (headline, subline) = when {
            opening && replay -> "Welcome back." to "Let's fine-tune your Home."
            opening -> "Welcome to your new Home." to "Let's make it yours."
            replay -> "All set." to "Your changes are saved."
            else -> "Make yourself at home." to "Your new Home is ready."
        }
        val view = SetupTitleView(context, headline, subline, choices.typeface, choices.motion)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, dp(280)))
        val action = button(if (opening) "Next" else "Enter Telum", "title") { if (opening) leaveTitle() else enter() }
        action.alpha = 0f
        action.translationY = dp(14).toFloat()
        action.isFocusable = false
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply { topMargin = dp(12) })
        titleAction = action
        view.play()
        postDelayed(revealAction, TITLE_ACTION_MS)
    }

    /** Starts the opening title again as the intro that covered it dissolves. */
    fun replayTitle() {
        if (!titling || !opening) return
        animate().cancel()
        alpha = 0f
        title(opening = true)
        animate().alpha(1f).setDuration(if (choices.motion) 900L else 300L).start()
    }

    private fun leaveTitle() = fadeTo(out = 700L, back = 450L) {
        titling = false
        titleAction = null
        render()
    }

    private fun enter() {
        val draft = selection ?: return
        if (transitioning) return
        transitioning = true
        animate().cancel()
        animate().alpha(0f).setDuration(if (choices.motion) 900L else 250L)
            .withEndAction { host.finish(choices, draft) }.start()
    }

    // ------------------------------------------------------------------ navigation

    private fun go(page: Int) {
        if (page == 3) connectionsFrom = step
        fadeTo {
            step = page
            prefs.setupStep = page
            render()
        }
    }

    private fun finish() {
        val draft = selection ?: return
        if (draft.selected.isEmpty()) { go(0); return }
        fadeTo(out = 600L, back = 400L) { title(opening = false) }
    }

    private fun exit() = fadeTo(out = 400L) { host.exit() }

    override fun onDetachedFromWindow() {
        removeCallbacks(revealAction)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    fun appsChanged(available: List<AppEntry>) {
        entries = available
        inventoryLoaded = true
        inventoryChanged = true
        val keys = entries.map { it.key }
        val visible = keys.filter { it !in prefs.hidden }.toSet()
        selection = (selection ?: SetupApps(keys, visible)).reconcile(keys, visible)
        prefs.saveSetupApps(selection!!)
        if (step == 3) refreshConnections()
        else if (!titling && !transitioning && step == 0) render()
    }

    fun artChanged(available: List<AppEntry>) {
        entries = available
        if (!inventoryChanged) picker?.updateEntries(available)
    }

    fun back(): Boolean {
        if (transitioning) return true
        if (titling) {
            if (opening && replay) exit()
            return true
        }
        if (step == 0 && picker?.handleBack() == true) return true
        if (step > 0) go(if (step == 3) connectionsFrom else step - 1)
        else if (replay) exit()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (transitioning) return true
        if (titling && titleAction?.isFocusable != true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) back()
            return true
        }
        val index = options.indexOfFirst { it.isFocused }
        if (index >= 0 && event.keyCode in ARROWS) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> options.getOrNull(index - 1)?.requestFocus()
                KeyEvent.KEYCODE_DPAD_DOWN -> (options.getOrNull(index + 1) ?: controls["next"])?.requestFocus()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (transitioning || (titling && titleAction?.isFocusable != true)) true else super.dispatchTouchEvent(event)

    /**
     * After a permission screen, the city dialog or a background read. With TV listings access
     * already granted (a rerun), the read for Live Activities can take only tens of milliseconds,
     * so it can finish before this page is attached or while it is still fading in. Neither may
     * drop the result: a page not yet attached renders anyway, and a fading one renders when the
     * fade ends.
     */
    fun refreshConnections() {
        if (step != 3 || titling) return
        if (transitioning) connectionsChanged = true else render(focusedKey())
    }

    private fun focusedKey() = controls.entries.firstOrNull { it.value.hasFocus() }?.key

    private fun update(next: SetupChoices, focus: String) {
        choices = next
        prefs.saveSetupDraft(choices)
        render(focus)
    }

    // ------------------------------------------------------------------ pages

    private fun clearPage() {
        removeAllViews()
        controls.clear()
        options.clear()
        scroller = null
        picker = null
        sideTitle = null
        sideText = null
    }

    private fun render(focus: String? = null) {
        inventoryChanged = false
        connectionsChanged = false
        val scrollY = if (focus != null) scroller?.scrollY ?: 0 else 0
        clearPage()
        gravity = Gravity.TOP
        val masthead = row()
        masthead.addView(label("telum", 24f, ink, 800), LayoutParams(0, dp(42), 1f))
        listOf("01  Your apps", "02  Your style", "03  Your features", "04  Final touches").forEachIndexed { index, text ->
            masthead.addView(label(text, 11f, if (index == step) accent else muted, if (index == step) 800 else 500),
                LayoutParams(dp(130), LayoutParams.WRAP_CONTENT))
        }
        addView(masthead)
        space(this, 10)
        addView(label(listOf("Bring over your favourites.", "How do you like your home screen?",
            "Choose what works for you.", "Final touches.")[step], 28f, ink, 700))
        space(this, 7)
        addView(label(listOf(
            "Choose the apps you want on Home, then put them in order.",
            "Pick a starting point. Customize lets you fine-tune it.",
            "Keep the features you want. Turn off the rest.",
            "Connect what you chose, or skip it for now.",
        )[step], 14f, muted))
        space(this, 20)
        val body = row()
        body.gravity = Gravity.TOP
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        when (step) {
            0 -> appSelection(body)
            1 -> presets(body)
            2 -> customize(body)
            3 -> connections(body)
        }
        if (step == 1) {
            space(this, 8)
            addView(label("Artist photos send artist names to TheAudioDB. Weather sends your city to Open-Meteo.", 11f, muted))
        }
        // The app page carries its own footer.
        if (step > 0) footer()
        val target = focus?.let { controls[it] } ?: when (step) {
            0 -> null
            1 -> controls[if (choices == SetupChoices.LIGHT) "light" else "recommended"]
            else -> options.firstOrNull() ?: controls["next"]
        }
        if (step == 0) picker?.focusFirstApp() ?: controls["retry"]?.requestFocus() else target?.requestFocus()
        // Restore the list's place, then keep the focused row on screen: a new font can change
        // every row's height.
        scroller?.post {
            scroller?.scrollTo(0, scrollY)
            val row = options.firstOrNull { it.isFocused } ?: return@post
            row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
        }
    }

    private fun footer() {
        space(this, 14)
        val footer = row()
        if (replay) footer.addView(button("Exit setup", "exit") { exit() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(10) })
        footer.addView(button("Back", "back") { back() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)))
        footer.addView(label(if (step == 2) "Press OK to change a setting." else "Change anytime in Control centre › Telum.",
            11f, muted).apply { setPadding(dp(16), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL },
            LayoutParams(0, dp(44), 1f))
        if (step == 1) footer.addView(button("Customize", "customize") { go(2) },
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(12) })
        footer.addView(button(when (step) {
            1 -> if (choices.presetName == "Custom") "Continue with my choices" else "Continue with ${choices.presetName}"
            3 -> if (replay) "Save and Enter" else "Start using Telum"
            else -> "Continue"
        }, "next") { if (step == 3) finish() else go(3) }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)))
        addView(footer)
        val next = controls.getValue("next")
        val up = if (step == 1) controls.getValue(if (choices == SetupChoices.LIGHT) "light" else "recommended") else options.lastOrNull()
        listOfNotNull(controls["exit"], controls["back"], controls["customize"], next).forEach { button ->
            button.nextFocusUpId = up?.id ?: button.id
            button.nextFocusDownId = button.id
        }
        if (step == 1) {
            controls.getValue("recommended").nextFocusDownId = next.id
            controls.getValue("light").nextFocusDownId = next.id
        }
    }

    private fun appSelection(body: LinearLayout) {
        if (!inventoryLoaded || entries.isEmpty()) {
            val status = column()
            status.addView(label(if (inventoryLoaded) "No apps found." else "Finding your apps…", 20f, muted))
            space(status, 18)
            status.addView(button("Try again", "retry") { host.retryApps() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)))
            body.addView(status)
            return
        }
        picker = SetupAppsView(context, selection!!, entries, choices.typeface, { draft ->
            selection = draft
            prefs.saveSetupApps(draft)
        }, { go(1) }, if (replay) ({ exit() }) else null)
        body.addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun presets(body: LinearLayout) {
        fun card(name: String, key: String, preset: SetupChoices, tagline: String, detail: String) {
            val selected = choices == preset
            val card = column().apply { setPadding(dp(20), dp(14), dp(20), dp(14)) }
            card.addView(label(if (selected) "Selected" else "", 10f, accent, 800))
            space(card, 5)
            card.addView(label(name, 24f, ink, 700))
            space(card, 6)
            card.addView(label(tagline, 14f, muted))
            space(card, 12)
            card.addView(HomePreview(context, preset), LayoutParams(LayoutParams.MATCH_PARENT, dp(76)))
            space(card, 10)
            card.addView(label(detail, 13f, ink).apply { setLineSpacing(dp(3).toFloat(), 1f) })
            interactive(card, key, selected) { update(preset, key) }
            card.contentDescription = "$name. $tagline. $detail. ${if (selected) "Selected" else "Press OK to select"}"
            body.addView(card, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                if (key == "recommended") marginEnd = dp(16)
            })
        }
        card("Recommended", "recommended", SetupChoices.RECOMMENDED, "A richer home screen.",
            "Colours from your apps and smooth animations.\nMusic controls, idle artwork and artist photos.\nLive Activities and weather for your city.")
        card("Light", "light", SetupChoices.LIGHT, "Just your apps.",
            "A plain background and reduced motion.\nNo music controls or artist photos.\nNo Live Activities or weather.")
        val recommended = controls.getValue("recommended")
        val light = controls.getValue("light")
        recommended.nextFocusRightId = light.id
        light.nextFocusLeftId = recommended.id
    }

    /** Explains the focused row, so the rows themselves stay one line each. */
    private fun side(body: LinearLayout, title: String, text: String) {
        val side = column().apply { setPadding(0, dp(4), dp(36), 0) }
        sideTitle = label(title, 20f, accent, 700)
        sideText = label(text, 14f, ink).apply { setLineSpacing(dp(5).toFloat(), 1f) }
        side.addView(sideTitle)
        space(side, 12)
        side.addView(sideText)
        body.addView(side, LayoutParams(dp(330), LayoutParams.MATCH_PARENT))
    }

    private fun explain(title: String, text: String) {
        sideTitle?.text = title
        sideText?.text = text
    }

    private fun list(body: LinearLayout): LinearLayout {
        val items = column()
        scroller = ScrollView(context).apply {
            isFillViewport = false
            isFocusable = false
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(28))
            setPadding(dp(2), dp(2), dp(8), dp(2))
            addView(items)
        }
        body.addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        return items
    }

    private fun option(parent: LinearLayout, key: String, title: String, value: String, detail: String,
                       enabled: Boolean = true, indent: Boolean = false, action: () -> Unit) {
        val item = row().apply { setPadding(dp(if (indent) 36 else 16), 0, dp(16), 0) }
        item.addView(label(title, if (indent) 15f else 16f, if (indent) muted else ink, if (indent) 600 else 700).apply {
            maxLines = 1
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        item.addView(label(value, 14f, accent, 700).apply { maxLines = 1; setPadding(dp(12), 0, 0, 0) })
        interactive(item, key) { if (enabled) action() }
        val paint = item.onFocusChangeListener
        item.setOnFocusChangeListener { view, focused ->
            paint.onFocusChange(view, focused)
            if (focused) explain(title, detail)
        }
        if (!enabled) item.alpha = .5f
        item.contentDescription = "$title. $value. $detail${if (enabled) " Press OK to change." else ""}"
        options += item
        parent.addView(item, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(6) })
    }

    private fun note(parent: LinearLayout, text: String) =
        parent.addView(label(text, 13f, muted).apply { setPadding(dp(36), dp(4), dp(16), dp(12)) })

    private fun customize(body: LinearLayout) {
        side(body, "Your features", "Select a setting to see what it does.")
        val items = list(body)
        fun toggle(key: String, title: String, on: Boolean, detail: String, enabled: Boolean = true, next: () -> SetupChoices) =
            option(items, key, title, if (on) "On" else "Off", detail, enabled) { update(next(), key) }
        option(items, "wallpaper", "Background", backgroundName(choices.wallpaper),
            "Follow the colours of the app you're on, or pick a fixed background.") {
            update(choices.copy(wallpaper = BACKGROUNDS[(BACKGROUNDS.indexOf(choices.wallpaper) + 1) % BACKGROUNDS.size]), "wallpaper")
        }
        option(items, "motion", "Motion", if (choices.motion) "Full" else "Reduced",
            "Full animations, or shorter and simpler ones.") { update(choices.copy(motion = !choices.motion), "motion") }
        toggle("watching", "Live Activities", choices.watching,
            "What you're watching, from apps that share it. Needs TV listings access.") { choices.copy(watching = !choices.watching) }
        toggle("music", "Music controls", choices.music,
            "Play, pause and skip from Home. Needs notification access.") { choices.copy(music = !choices.music) }
        val needsMusic = "Turn on Music controls first."
        toggle("idle", "Music idle display", choices.musicIdle,
            if (choices.music) "Shows dimmed album art after a minute without input while music plays." else needsMusic,
            choices.music) { choices.copy(musicIdle = !choices.musicIdle) }
        toggle("photos", "Artist photos", choices.artistPhotos,
            if (choices.music) "Shows artist photos while music plays. Artist names are sent to TheAudioDB." else needsMusic,
            choices.music) { choices.copy(artistPhotos = !choices.artistPhotos) }
        toggle("weather", "Weather", choices.weather,
            "Shows the forecast for a city you choose. The city is sent to Open-Meteo.") { choices.copy(weather = !choices.weather) }
        option(items, "names", "App names", if (choices.names) "Always" else "When highlighted",
            "When names appear under your apps.") { update(choices.copy(names = !choices.names), "names") }
        option(items, "clock", "Clock", if (choices.clock24) "24-hour" else "12-hour",
            "12-hour or 24-hour time.") { update(choices.copy(clock24 = !choices.clock24), "clock") }
        option(items, "units", "Temperature", if (choices.fahrenheit) "Fahrenheit" else "Celsius",
            "Celsius or Fahrenheit.") { update(choices.copy(fahrenheit = !choices.fahrenheit), "units") }
        option(items, "font", "Font", Fonts.name(choices.typeface),
            "The typeface used throughout Telum.") { update(choices.copy(typeface = if (choices.typeface == "manrope") "inter" else "manrope"), "font") }
    }

    private fun backgroundName(id: String) = if (id == "dynamic") "App colours" else id.replaceFirstChar { it.uppercase() }

    private fun connections(body: LinearLayout) {
        val optional = "Everything here is optional. A feature you skip starts working once you connect it."
        side(body, "All optional", optional)
        val items = list(body)
        if (choices.music) {
            val steps = host.musicSteps().mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            option(items, "connectMusic", "Music controls", if (host.musicReady()) "Connected" else "Open Settings",
                if (host.musicReady()) "Connected. Telum doesn't read or keep your notifications."
                else "Turn on notification access in Settings:\n$steps\n\nTelum doesn't read or keep your notifications.") { host.connectMusic() }
        }
        if (choices.watching) {
            val ready = host.watchingReady()
            option(items, "connectWatching", "Live Activities", if (ready) "Connected" else "Allow access",
                "Allow TV listings access to see what you're watching on Home. What appears depends on each app.") { host.connectWatching() }
            if (ready) activities(items)
        }
        if (choices.weather) option(items, "city", "Weather city",
            prefs.setupLocation.place.substringBefore(',').ifBlank { "Choose city" },
            "Your search is sent to Open-Meteo. Telum doesn't use your device's location.") { host.chooseCity() }
        if (!choices.music && !choices.watching && !choices.weather) {
            explain("Nothing else to set up.", "Your apps and settings are ready.")
        }
    }

    /** What each chosen app shows in Live Activities, chosen here so Home isn't empty at first. */
    private fun activities(items: LinearLayout) {
        val found = shelves
        if (found == null && !previewing) {
            previewing = true
            host.previewActivities { result ->
                previewing = false
                shelves = result
                refreshConnections()
            }
        }
        // Setup resumed after a restart reads the rows and the app list at the same time.
        if (found == null || !inventoryLoaded) {
            note(items, "Checking your apps…")
            return
        }
        val chosen = selection?.let { s -> s.order.filter { it in s.selected } }.orEmpty()
        val apps = chosen.mapNotNull { key -> entries.firstOrNull { it.key == key } }
            .distinctBy { it.pkg }.filter { !found[it.pkg].isNullOrEmpty() }
        if (apps.isEmpty()) {
            note(items, "None of your apps share activities yet.")
            return
        }
        for (app in apps) {
            val rows = found.getValue(app.pkg)
            val auto = rows.any { it.progress }
            val choicesForApp = (if (auto) listOf("auto") else emptyList()) + "off" + rows.map { it.name }.distinct()
            fun current() = prefs.setupRowChoice(app.pkg) ?: if (auto) "auto" else "off"
            val shown = when (val c = current()) { "auto" -> "Automatic"; "off" -> "Off"; else -> c }
            option(items, "row:${app.pkg}", app.label, shown,
                "Choose what ${app.label} shows on Home." + if (auto) " Automatic shows what you're watching." else "",
                indent = true) {
                val next = choicesForApp[(choicesForApp.indexOf(current()).coerceAtLeast(0) + 1) % choicesForApp.size]
                prefs.setSetupRowChoice(app.pkg, if (next == "auto" || (next == "off" && !auto)) null else next)
                render("row:${app.pkg}")
            }
        }
    }

    /** Local schematic only. It never fetches artwork, weather or media during setup. */
    private class HomePreview(context: Context, private val choices: SetupChoices) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = intArrayOf(0xff668d98.toInt(), 0xff8a789d.toInt(), 0xff638f82.toInt(), 0xffa18570.toInt(), 0xff708cae.toInt())
        private val mood = Palette.theme(choices.wallpaper)
        private val backgroundColors = if (choices.wallpaper == "dynamic") intArrayOf(0xff285762.toInt(), 0xff3e305b.toInt())
            else intArrayOf(mood.a, mood.base)
        private var gradient: LinearGradient? = null
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            gradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), backgroundColors, null, Shader.TileMode.CLAMP)
        }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.shader = gradient
            canvas.drawRoundRect(0f, 0f, w, h, 12f, 12f, paint)
            paint.shader = null
            paint.color = 0xffdbe6ed.toInt()
            paint.textSize = h * .085f
            canvas.drawText("telum", w * .06f, h * .19f, paint)
            canvas.drawText(if (choices.clock24) "20:30" else "8:30", w * .81f, h * .19f, paint)
            if (choices.music) {
                paint.color = 0xff9fdccc.toInt()
                canvas.drawRoundRect(w * .06f, h * .3f, w * .17f, h * .58f, 6f, 6f, paint)
                paint.color = 0xffdbe6ed.toInt()
                canvas.drawText("Your music", w * .2f, h * .44f, paint)
            }
            for (i in 0..4) {
                paint.color = colors[i]
                val left = w * (.06f + i * .18f)
                canvas.drawRoundRect(left, h * .69f, left + w * .16f, h * .89f, 5f, 5f, paint)
            }
        }
    }
}
