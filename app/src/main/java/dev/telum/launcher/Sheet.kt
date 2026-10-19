package dev.telum.launcher

/** One row in a menu or in the side sheet. */
class SheetItem(
    val glyph: String?,
    val label: String,
    /** Current value shown on the right (for settings). */
    val value: (() -> String)? = null,
    /** Left/right (and OK) step through choices: called with -1 or +1. */
    val choice: ((Int) -> Unit)? = null,
    /** Shows a chevron: opens another page. */
    val chevron: Boolean = false,
    val enabled: Boolean = true,
    val action: (() -> Unit)? = null,
)

class SheetPage(
    val id: String,
    val title: String?,
    /** The Control Center page shows the clock and weather above its items. */
    val header: Boolean = false,
    val build: () -> List<SheetItem>,
) {
    var items: List<SheetItem> = build()
        private set
    var focus = 0
    val scroll = Spring(0f, 420f, 1f)

    fun rebuild() {
        items = build()
        focus = focus.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}
