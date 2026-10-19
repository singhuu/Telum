package dev.telum.launcher

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Four colours that drive the ambient backdrop while an app is focused. */
data class Mood(val base: Int, val a: Int, val b: Int, val c: Int) {
    companion object {
        val DEFAULT = Mood(0xFF070912.toInt(), 0xFF3B3FA8.toInt(), 0xFF7A3E9D.toInt(), 0xFF1E6B8F.toInt())

        /**
         * The light colours travel round the colour wheel (shortest way) instead of in a straight
         * RGB line, so violet to amber sweeps through magenta and red rather than muddy brown.
         */
        fun lerp(from: Mood, to: Mood, t: Float): Mood {
            if (t <= 0f) return from
            if (t >= 1f) return to
            return Mood(
                lerpColor(from.base, to.base, t),
                lerpHue(from.a, to.a, t),
                lerpHue(from.b, to.b, t),
                lerpHue(from.c, to.c, t),
            )
        }
    }
}

private val hsvA = FloatArray(3)
private val hsvB = FloatArray(3)
private val hsvOut = FloatArray(3)

/** HSV interpolation along the shorter hue arc; greys borrow the other colour's hue. */
fun lerpHue(a: Int, b: Int, t: Float): Int {
    Color.colorToHSV(a, hsvA)
    Color.colorToHSV(b, hsvB)
    if (hsvA[1] < 0.08f) hsvA[0] = hsvB[0]
    if (hsvB[1] < 0.08f) hsvB[0] = hsvA[0]
    var d = hsvB[0] - hsvA[0]
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    hsvOut[0] = (hsvA[0] + d * t + 360f) % 360f
    hsvOut[1] = hsvA[1] + (hsvB[1] - hsvA[1]) * t
    hsvOut[2] = hsvA[2] + (hsvB[2] - hsvA[2]) * t
    return Color.HSVToColor(hsvOut)
}

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
    )
}

fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)

object Palette {

    private fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf((h % 360f + 360f) % 360f, s, v))

    /** Known apps have a local palette even while signed out or without readable artwork. */
    fun appMood(packageName: String, artwork: Bitmap? = null): Mood {
        val profile = AppSupport.profile(packageName)
        if (profile == null && artwork != null) return moodOf(artwork)
        val h1 = profile?.primaryHue ?: AppSupport.fallbackHue(packageName)
        val h2 = profile?.secondaryHue ?: analogous(h1)
        val saturation = profile?.saturation ?: 0.72f
        return Mood(
            base = luma(hsv(h1, saturation, 0.2f), 0.01f),
            a = luma(hsv(h1, saturation, 0.8f), 0.105f),
            b = luma(hsv(h2, saturation, 0.8f), 0.07f),
            c = luma(hsv(h2, saturation * 0.9f, 0.6f), 0.03f),
        )
    }

    /**
     * Picks the artwork's strongest hues, weighted by saturation and coverage, then rebuilds them
     * at controlled brightness so every app gets a rich backdrop that white text stays legible on.
     */
    fun moodOf(bitmap: Bitmap): Mood {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 18, true)
        val px = IntArray(32 * 18)
        small.getPixels(px, 0, 32, 0, 0, 32, 18)
        if (small !== bitmap) small.recycle()

        val buckets = 18
        val weight = FloatArray(buckets)
        val sumS = FloatArray(buckets)
        val sumV = FloatArray(buckets)
        val sumX = FloatArray(buckets)
        val sumY = FloatArray(buckets)
        val hsv = FloatArray(3)
        var lum = 0f
        for (p in px) {
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            lum += hsv[2]
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.18f || v < 0.12f) continue
            val w = s * s * (0.35f + v)
            val bucket = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            weight[bucket] += w
            sumS[bucket] += s * w
            sumV[bucket] += v * w
            // Circular mean of hue inside the bucket.
            val rad = Math.toRadians(hsv[0].toDouble())
            sumX[bucket] += (Math.cos(rad) * w).toFloat()
            sumY[bucket] += (Math.sin(rad) * w).toFloat()
        }
        val order = (0 until buckets).sortedByDescending { weight[it] }
        val total = weight.sum()
        if (total < 0.35f) {
            // Monochrome artwork (black, white or grey): a cool graphite mood.
            val bright = lum / px.size > 0.55f
            return if (bright) Mood(0xFF0B0C10.toInt(), 0xFF5A6070.toInt(), 0xFF3A4152.toInt(), 0xFF6C6F7E.toInt())
            else Mood(0xFF07080B.toInt(), 0xFF3C4250.toInt(), 0xFF262B38.toInt(), 0xFF4D5262.toInt())
        }

        fun hueOf(b: Int): Float {
            val deg = Math.toDegrees(Math.atan2(sumY[b].toDouble(), sumX[b].toDouble())).toFloat()
            return (deg + 360f) % 360f
        }

        // Dark yellow reads as olive; warm golds are pulled towards amber.
        val h1 = hueOf(order[0]).let { if (it in 38f..72f) 36f + (it - 38f) * 0.15f else it }
        val s1 = sumS[order[0]] / weight[order[0]]
        // Second hue: the next bucket that is meaningfully different, else an analogous hue that
        // stays out of the muddy yellow-green band (yellows lean towards amber and red instead).
        val second = order.drop(1).firstOrNull {
            weight[it] > total * 0.1f && hueDistance(hueOf(it), h1) > 28f && !muddy(hueOf(it))
        }
        val h2 = second?.let { hueOf(it) } ?: analogous(h1)
        val s2 = second?.let { sumS[it] / weight[it] } ?: s1

        val sat1 = max(0.6f, min(0.9f, s1 * 1.1f))
        val sat2 = max(0.55f, min(0.88f, s2 * 1.05f))
        // Colours are set by perceived brightness, so yellow is not glaring and blue not murky.
        return Mood(
            base = luma(hsv(h1, min(0.8f, sat1), 0.2f), 0.01f),
            a = luma(hsv(h1, sat1, 0.8f), 0.105f),
            b = luma(hsv(h2, sat2, 0.8f), 0.07f),
            c = luma(hsv(h2, sat2 * 0.9f, 0.6f), 0.03f),
        )
    }

    /** The greeting's backdrop follows the time of day: dawn, daylight, dusk and night. */
    fun timeOfDay(hour: Int): Mood = when (hour) {
        in 5..9 -> Mood(0xFF0D0808.toInt(), 0xFFB8644A.toInt(), 0xFF6E4A8C.toInt(), 0xFF3B3A6E.toInt())
        in 10..16 -> Mood(0xFF050B14.toInt(), 0xFF2D6FB0.toInt(), 0xFF2A8C9A.toInt(), 0xFF22427A.toInt())
        in 17..20 -> Mood(0xFF0C0610.toInt(), 0xFFB0503E.toInt(), 0xFF6A2F7A.toInt(), 0xFF2C2A66.toInt())
        else -> Mood(0xFF04050C.toInt(), 0xFF27306E.toInt(), 0xFF4A2A6A.toInt(), 0xFF142A4A.toInt())
    }

    private fun muddy(h: Float) = h in 48f..100f

    private fun analogous(h: Float): Float {
        val hh = (h % 360f + 360f) % 360f
        return when {
            hh in 30f..110f -> hh - 38f // yellow/olive/lime -> amber/orange
            hh in 110f..200f -> hh + 32f // green/teal -> cyan/blue
            hh in 200f..290f -> hh + 34f // blue/indigo -> violet/magenta
            else -> hh - 30f // magenta/red -> pink/crimson
        }
    }

    private fun linear(c: Int): Float {
        val s = c / 255f
        return if (s <= 0.04045f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    private fun luminance(color: Int): Float =
        0.2126f * linear(Color.red(color)) + 0.7152f * linear(Color.green(color)) + 0.0722f * linear(Color.blue(color))

    /** Rescales the colour's HSV value until its relative luminance is close to [target]. */
    private fun luma(color: Int, target: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        var lo = 0f
        var hi = 1f
        repeat(18) {
            val mid = (lo + hi) / 2f
            val c = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], mid))
            if (luminance(c) < target) lo = mid else hi = mid
        }
        return Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], (lo + hi) / 2f))
    }

    /** A fixed mood for wallpaper themes that do not follow the focused app. */
    fun theme(name: String): Mood = when (name) {
        "ocean" -> Mood(0xFF040A12.toInt(), 0xFF1667A8.toInt(), 0xFF0E8C8C.toInt(), 0xFF27358F.toInt())
        "sunset" -> Mood(0xFF0E0608.toInt(), 0xFFD0563A.toInt(), 0xFF9C2F6E.toInt(), 0xFFE0A040.toInt())
        "forest" -> Mood(0xFF040A07.toInt(), 0xFF2E8A55.toInt(), 0xFF1D6670.toInt(), 0xFF7A9A34.toInt())
        "graphite" -> Mood(0xFF08090B.toInt(), 0xFF454B5A.toInt(), 0xFF2C313D.toInt(), 0xFF5E6373.toInt())
        else -> Mood.DEFAULT
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
