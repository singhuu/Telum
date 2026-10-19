package dev.telum.launcher

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A damped spring. Every moving thing on the home screen is one of these, so motion keeps its
 * velocity when a target changes mid-flight (fast D-pad presses never restart an animation).
 */
class Spring(
    initial: Float = 0f,
    stiffness: Float = 320f,
    dampingRatio: Float = 0.82f,
) {
    var value = initial
    var velocity = 0f
    var target = initial
    private var k = stiffness
    private var c = 2f * dampingRatio * sqrt(stiffness)

    fun tune(stiffness: Float, dampingRatio: Float): Spring {
        k = stiffness
        c = 2f * dampingRatio * sqrt(stiffness)
        return this
    }

    fun snap(v: Float) {
        value = v; target = v; velocity = 0f
    }

    val atRest: Boolean
        get() {
            // At tile/scroll coordinates a Float can stop changing before the old fixed
            // epsilon is reached. Account for its precision so invisible motion ends.
            val epsilon = maxOf(0.0005f, Math.ulp(maxOf(abs(target), abs(value))) * 16f)
            return abs(target - value) < epsilon && abs(velocity) < maxOf(0.002f, epsilon * sqrt(k))
        }

    /** Advances by [dt] seconds. Returns true while still moving. */
    fun step(dt: Float): Boolean {
        if (atRest) {
            value = target; velocity = 0f
            return false
        }
        // Fixed sub-steps keep stiff springs stable when a frame is late.
        var remaining = dt.coerceAtMost(0.064f)
        while (remaining > 0f) {
            val h = remaining.coerceAtMost(1f / 240f)
            val a = k * (target - value) - c * velocity
            velocity += a * h
            value += velocity * h
            remaining -= h
        }
        return true
    }
}
