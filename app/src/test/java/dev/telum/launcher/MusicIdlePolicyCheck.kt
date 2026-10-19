package dev.telum.launcher

/** Runs on a plain JVM; no device, sleeps, or Android mocks. */
fun main() {
    val idle = MusicIdlePolicy()
    idle.update(10_000L, true)
    check(!idle.dimmed && idle.delay(10_000L) == 60_000L)
    idle.update(69_999L, true)
    check(!idle.dimmed && idle.delay(69_999L) == 1L)
    idle.update(70_000L, true)
    check(idle.dimmed && idle.delay(70_000L) == null)

    // Repeated session/metadata callbacks and a new track do not postpone inactivity.
    idle.interact(100_000L, true)
    for (now in 101_000L..159_000L step 1_000L) idle.update(now, true)
    idle.update(160_000L, true)
    check(idle.dimmed)

    // The wake input restores once, and earns a fresh minute before dimming again.
    check(idle.interact(165_000L, true))
    check(!idle.interact(165_010L, true))
    idle.update(225_009L, true)
    check(!idle.dimmed)
    idle.update(225_010L, true)
    check(idle.dimmed)

    // Paused, backgrounded, hidden, menus, or held OK all revoke eligibility.
    idle.update(230_000L, false)
    check(!idle.dimmed && idle.delay(300_000L) == null)
    idle.update(400_000L, true)
    check(!idle.dimmed && idle.delay(400_000L) == 60_000L)
    idle.update(459_999L, true)
    check(!idle.dimmed)
    idle.update(460_000L, true)
    check(idle.dimmed)

    // Input while ineligible cannot arm a deadline until playback/home becomes eligible.
    check(idle.interact(470_000L, false))
    check(idle.delay(500_000L) == null)
    idle.update(700_000L, true)
    check(!idle.dimmed && idle.delay(700_000L) == 60_000L)
    println("Music idle policy: all checks passed")
}
