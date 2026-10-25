package dev.telum.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Remote-friendly list for choosing the apps on Home and their order. Names, not banners: the
 * list reads at a distance and fits many apps. Arranging shows only the chosen apps, grouped in
 * Home's rows of [HOME_COLUMNS].
 */
@SuppressLint("ViewConstructor") // Created by OnboardingView with its draft and callbacks, never from XML.
class SetupAppsView(
    context: Context,
    initial: SetupApps,
    entries: List<AppEntry>,
    private val font: String,
    private val onChanged: (SetupApps) -> Unit,
    private val onContinue: () -> Unit,
    onExit: (() -> Unit)?,
) : LinearLayout(context) {
    private val ink = Color.rgb(244, 246, 252)
    private val muted = Color.rgb(168, 181, 201)
    private val accent = Color.rgb(164, 228, 211)
    private val dark = Color.rgb(15, 36, 35)
    private var saved = initial
    private var names = entries.associate { it.key to it.label }
    private var arranging = false
    private var movingKey: String? = null
    private var movePreview: SetupApps? = null
    private val rows = linkedMapOf<String, AppRow>()
    private var shownKeys = emptyList<String>()
    /** The row the side buttons and Continue return to. */
    private var lastRowKey: String? = null

    private val count = label("", 17f, accent, 700)
    private val all: TextView = button("Select all") { commit(saved.selectAll()); refreshRows() }
    private val clear: TextView = button("Clear all") { commit(saved.clear()); refreshRows() }
    private val arrange: TextView = button("Arrange apps") { toggleArrange() }
    private val help = label("", 13f, ink, 500)
    private val next: TextView = button("Continue") { if (saved.selected.isNotEmpty() && movingKey == null) onContinue() }
    private val exit: TextView? = onExit?.let { action -> button("Exit setup") { action() } }
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = ScrollView(context).apply {
        isFocusable = false
        isFillViewport = false
        clipToPadding = false
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(32))
        setPadding(0, dp(4), dp(4), dp(4))
        addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    val draft: SetupApps get() = saved

    init {
        orientation = VERTICAL
        count.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        val side = LinearLayout(context).apply { orientation = VERTICAL }
        side.addView(count)
        listOf(all, clear, arrange).forEachIndexed { index, view ->
            side.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(if (index == 0) 16 else 10) })
        }
        side.addView(View(context), LayoutParams(1, 0, 1f))
        side.addView(help)
        side.addView(label("Apps you leave out stay installed. Find them in Control centre › Telum › Hidden apps.", 11f, muted)
            .apply { setPadding(0, dp(8), 0, 0) })
        val body = LinearLayout(context).apply { orientation = HORIZONTAL }
        body.addView(side, LayoutParams(dp(240), LayoutParams.MATCH_PARENT))
        body.addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(36) })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        exit?.let { footer.addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44))) }
        footer.addView(View(context), LayoutParams(0, 1, 1f))
        footer.addView(next, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(14) })
        rebuild()
    }

    fun focusFirstApp() { rows.values.firstOrNull()?.requestFocus() ?: all.requestFocus() }

    /** Names update in place. A changed inventory keeps earlier choices and rebuilds the list. */
    fun updateEntries(entries: List<AppEntry>) {
        val updated = entries.associate { it.key to it.label }
        val changedInventory = names.keys != updated.keys
        names = updated
        if (changedInventory) {
            movingKey = null
            movePreview = null
            commit(saved.reconcile(entries.map { it.key }, emptySet()))
            if (saved.selected.isEmpty()) arranging = false
            rebuild()
        } else {
            rows.forEach { (key, row) -> row.name.text = names[key] }
        }
    }

    /** Back first abandons an unplaced move, then leaves arranging. */
    fun handleBack(): Boolean {
        val key = movingKey
        if (key != null) {
            movingKey = null
            movePreview = null
            rebuild(key)
            return true
        }
        if (arranging) {
            toggleArrange()
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = movingKey
        if (key != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) shift(key, if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    if (event.action == KeyEvent.ACTION_UP) placeMove()
                    return true
                }
            }
        }
        // One press moves one row. The scroll view's own arrow handling can spend a press
        // scrolling, and it cannot leave the list for the buttons.
        val focusedKey = rows.entries.firstOrNull { it.value.isFocused }?.key
        if (focusedKey != null && event.keyCode in ARROWS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val index = shownKeys.indexOf(focusedKey)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> rows[shownKeys.getOrNull(index - 1)]?.requestFocus()
                    KeyEvent.KEYCODE_DPAD_DOWN -> (rows[shownKeys.getOrNull(index + 1)] ?: bottomTarget()).requestFocus()
                    KeyEvent.KEYCODE_DPAD_LEFT -> sideTarget().requestFocus()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun shift(key: String, delta: Int) {
        val index = shownKeys.indexOf(key)
        val target = index + delta
        if (index < 0 || target !in shownKeys.indices) return
        // Only the chosen apps are shown while arranging; unchosen apps keep their places.
        val state = movePreview ?: saved
        val reordered = shownKeys.toMutableList().apply { add(target, removeAt(index)) }
        val chosen = reordered.iterator()
        movePreview = state.copy(order = state.order.map { if (it in state.selected) chosen.next() else it })
        rebuild(key)
    }

    private fun toggleArrange() {
        arranging = !arranging
        rebuild()
        if (arranging) rows.values.firstOrNull()?.requestFocus() else arrange.requestFocus()
    }

    private fun activate(key: String) {
        when {
            !arranging -> { commit(saved.toggle(key)); refreshRows() }
            movingKey == null -> { movingKey = key; movePreview = saved; refreshRows() }
            else -> placeMove()
        }
    }

    private fun placeMove() {
        val key = movingKey ?: return
        val placed = movePreview ?: saved
        movingKey = null
        movePreview = null
        commit(placed)
        rebuild(key)
    }

    private fun commit(value: SetupApps) {
        if (value == saved) return
        saved = value
        onChanged(value)
    }

    private fun rebuild(focusKey: String? = null) {
        val focused = focusKey ?: rows.entries.firstOrNull { it.value.hasFocus() }?.key
        val state = movePreview ?: saved
        shownKeys = state.order.filter { it in names && (!arranging || it in state.selected) }
        list.removeAllViews()
        rows.clear()
        shownKeys.forEachIndexed { index, key ->
            if (arranging && index % HOME_COLUMNS == 0) {
                list.addView(label(if (index == 0) "Top row" else "Row ${index / HOME_COLUMNS + 1}", 11f, accent, 800).apply {
                    setPadding(dp(16), dp(if (index == 0) 2 else 14), 0, dp(6))
                })
            }
            val row = AppRow(key)
            rows[key] = row
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply { bottomMargin = dp(4) })
        }
        if (shownKeys.isEmpty()) list.addView(label("No apps found.", 18f, muted).apply { setPadding(dp(16), dp(12), 0, 0) })
        refreshRows()
        if (focused != null) (rows[focused] ?: rows.values.firstOrNull() ?: all).requestFocus()
    }

    private fun refreshRows() {
        val chosen = saved.selected.size
        count.text = resources.getQuantityString(R.plurals.setup_apps_selected, names.size, chosen, names.size)
        help.text = when {
            movingKey != null -> "Up and Down move it. OK places it. Back cancels."
            arranging -> "Press OK on an app to pick it up. The first five fill Home's top row."
            chosen == 0 -> "Select at least one app to continue."
            else -> "Press OK to add or remove an app."
        }
        arrange.text = if (arranging) "Done arranging" else "Arrange apps"
        setAvailable(all, !arranging && chosen < names.size)
        setAvailable(clear, !arranging && chosen > 0)
        setAvailable(arrange, movingKey == null && chosen > 0)
        setAvailable(next, chosen > 0 && movingKey == null)
        rows.values.forEach { it.refresh() }
        wire()
    }

    private fun sideTarget(): View = listOf(arrange, clear, all).firstOrNull { it.isEnabled } ?: next

    private fun bottomTarget(): View = if (next.isEnabled) next else exit ?: sideTarget()

    /** Routes for the buttons. Rows route themselves in [dispatchKeyEvent]. */
    private fun wire() {
        val side = listOf(all, clear, arrange).filter { it.isEnabled }
        val back = (rows[lastRowKey] ?: rows.values.firstOrNull())?.id ?: View.NO_ID
        side.forEachIndexed { index, view ->
            view.nextFocusUpId = side.getOrNull(index - 1)?.id ?: view.id
            view.nextFocusDownId = side.getOrNull(index + 1)?.id ?: bottomTarget().id
            view.nextFocusRightId = if (back != View.NO_ID) back else view.id
            view.nextFocusLeftId = view.id
        }
        next.nextFocusUpId = (rows[lastRowKey] ?: rows.values.lastOrNull())?.id ?: sideTarget().id
        next.nextFocusLeftId = exit?.id ?: sideTarget().id
        next.nextFocusRightId = next.id
        exit?.let {
            it.nextFocusUpId = sideTarget().id
            it.nextFocusLeftId = it.id
            it.nextFocusRightId = if (next.isEnabled) next.id else it.id
        }
    }

    private fun setAvailable(view: TextView, available: Boolean) {
        view.isEnabled = available
        view.isFocusable = available
        view.alpha = if (available) 1f else .4f
        paintButton(view)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun panel(color: Int, stroke: Int = Color.TRANSPARENT, radius: Int = 12) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(2), stroke)
    }

    private fun label(value: String, size: Float, color: Int = ink, weight: Int = 500) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Fonts.face(context.assets, font, weight)
        includeFontPadding = false
    }

    /** A Material Symbols drawable in [color]. Mutated, so the tint stays with this copy. */
    private fun symbol(id: Int, color: Int) = context.getDrawable(id)!!.mutate().apply { setTint(color) }

    private fun paintButton(view: TextView) {
        view.background = panel(if (view.hasFocus()) Color.rgb(42, 62, 73) else Color.rgb(25, 33, 46),
            if (view.hasFocus()) ink else Color.rgb(52, 62, 78))
    }

    private fun button(title: String, action: () -> Unit) = label(title, 14f, ink, 700).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        maxLines = 1
        minWidth = dp(140)
        setPadding(dp(22), 0, dp(22), 0)
        setOnClickListener { action() }
        setOnFocusChangeListener { _, _ -> paintButton(this) }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
        paintButton(this)
    }

    private inner class AppRow(private val key: String) : LinearLayout(context) {
        private val mark = label("", 15f, dark, 800).apply {
            gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val name = label(names[key] ?: key, 21f, ink, 600).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val state = label("", 12f, accent, 700).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val tick = symbol(R.drawable.symbol_check_circle, accent)
        private val ring = symbol(R.drawable.symbol_circle, Color.rgb(108, 122, 143))

        init {
            id = View.generateViewId()
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(16), 0, dp(18), 0)
            addView(mark, LayoutParams(dp(28), dp(28)))
            addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(18) })
            addView(state)
            setOnClickListener { activate(key) }
            setOnFocusChangeListener { _, focused ->
                if (focused) { lastRowKey = key; wire() }
                refresh()
            }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = if (arranging) "android.widget.Button" else "android.widget.CheckBox"
                    info.isCheckable = !arranging
                    info.isChecked = key in saved.selected
                }
            }
        }

        fun refresh() {
            val selected = key in saved.selected
            val moving = key == movingKey
            val index = shownKeys.indexOf(key)
            mark.text = when { moving -> "↕"; arranging -> "${index + 1}"; else -> "" }
            mark.background = when {
                arranging -> panel(accent, radius = 14)
                selected -> tick
                else -> ring
            }
            name.setTextColor(if (selected || arranging) ink else muted)
            state.text = if (moving) "Moving" else ""
            background = when {
                moving -> panel(Color.rgb(32, 70, 66), accent)
                hasFocus() -> panel(Color.rgb(42, 62, 73), ink)
                else -> null
            }
            val label = names[key] ?: key
            contentDescription = if (arranging) {
                "$label. Position ${index + 1} of ${shownKeys.size}. " +
                    if (moving) "Moving. Press OK to place, or Back to cancel." else "Press OK to pick it up."
            } else {
                "$label. ${if (selected) "Selected. Press OK to remove." else "Not selected. Press OK to add."}"
            }
        }
    }
}

internal val ARROWS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
