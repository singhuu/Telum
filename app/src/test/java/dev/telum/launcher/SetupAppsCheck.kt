package dev.telum.launcher

/** Pure setup app behavior. No Android runtime or installed apps are required. */
fun main() {
    val original = SetupApps(listOf("plex", "music", "video"), setOf("plex", "video"))
    val toggled = original.toggle("music").toggle("plex")
    check(toggled == SetupApps(listOf("plex", "music", "video"), setOf("music", "video")))
    check(original.toggle("unknown") == original)
    check(original.selectAll().selected == setOf("plex", "music", "video"))
    check(original.clear() == SetupApps(listOf("plex", "music", "video"), emptySet()))
    check(original.clear().selectAll().order == listOf("plex", "music", "video"))

    val moved = original.move("video", -1)
    check(moved == SetupApps(listOf("plex", "video", "music"), setOf("plex", "video")))
    check(moved.move("video", 1) == original)
    check(original.move("plex", -1) == original)
    check(original.move("video", 1) == original)
    check(original.move("unknown", -1) == original)
    check(original.move("plex", Int.MAX_VALUE).order == listOf("music", "video", "plex"))
    check(original.move("video", Int.MIN_VALUE).order == listOf("video", "plex", "music"))

    // Existing unselected apps stay unselected even when new-app defaults include them.
    val reconciled = original.reconcile(listOf("music", "video", "new", "hidden", "new"),
        setOf("music", "new"))
    check(reconciled == SetupApps(listOf("music", "video", "new", "hidden"), setOf("video", "new")))
    check(reconciled.reconcile(listOf("music", "video", "new", "hidden"), emptySet()) == reconciled)
    check(original.reconcile(emptyList(), setOf("plex")) == SetupApps(emptyList(), emptySet()))
    check(SetupApps(emptyList(), emptySet()).reconcile(listOf("new", "hidden"), setOf("new")) ==
        SetupApps(listOf("new", "hidden"), setOf("new")))
    check(SetupApps(emptyList(), emptySet()).move("missing", 1) == SetupApps(emptyList(), emptySet()))

    // Editing a setup copy must leave the saved Home choices available for cancellation.
    check(original == SetupApps(listOf("plex", "music", "video"), setOf("plex", "video")))
    check(SetupLocation().place.isEmpty())
    check(SetupLocation().lat.isNaN() && SetupLocation().lon.isNaN())
    println("Setup app checks passed: selection, ordering, inventory changes, and unchanged source draft.")
}
