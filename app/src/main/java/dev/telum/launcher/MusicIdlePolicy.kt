package dev.telum.launcher

/** Monotonic inactivity deadline. Playback updates never count as user input. */
internal class MusicIdlePolicy(private val idleAfterMs: Long = 60_000L) {
    private var eligibleSince: Long? = null
    var dimmed = false
        private set

    fun update(now: Long, eligible: Boolean) {
        if (!eligible) {
            eligibleSince = null
            dimmed = false
            return
        }
        val since = eligibleSince
        if (since == null) eligibleSince = now
        else if (now - since >= idleAfterMs) dimmed = true
    }

    /** Returns whether this input restored the apps. */
    fun interact(now: Long, eligible: Boolean): Boolean {
        val wasDimmed = dimmed
        dimmed = false
        eligibleSince = if (eligible) now else null
        return wasDimmed
    }

    /** No timer is needed when paused, hidden, or already dimmed. */
    fun delay(now: Long): Long? = eligibleSince?.takeUnless { dimmed }
        ?.let { (idleAfterMs - (now - it)).coerceAtLeast(0L) }
}
