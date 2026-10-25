package dev.telum.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.sin

/**
 * The opening and closing titles of setup. The wordmark settles, the headline's words rise in one
 * by one and catch a passing light, then the line beneath appears. The sequence lasts [DURATION]
 * and then the view stops drawing. Reduced motion keeps the timing and only fades.
 */
@SuppressLint("ViewConstructor") // Created by OnboardingView with its text, never from XML.
class SetupTitleView(
    context: Context,
    private val headline: String,
    private val subline: String,
    font: String,
    private val motion: Boolean,
) : View(context) {
    companion object {
        const val DURATION = 5000L
    }

    private val ink = Color.rgb(244, 246, 252)
    private val muted = Color.rgb(168, 181, 201)
    private val accent = Color.rgb(164, 228, 211)
    private val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.face(context.assets, font, 800)
        textSize = sp(22f)
        color = accent
        textAlign = Paint.Align.CENTER
    }
    private val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.face(context.assets, font, 800)
        color = ink
    }
    private val sheen = TextPaint(head)
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.face(context.assets, font, 500)
        textSize = sp(24f)
        color = muted
        textAlign = Paint.Align.CENTER
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenMatrix = Matrix()
    private var sheenShader: LinearGradient? = null
    private var words = emptyList<Pair<String, Float>>()
    private var headLeft = 0f
    private var headWidth = 0f
    private var markY = 0f
    private var headY = 0f
    private var subY = 0f
    private var glowY = 0f
    private var started = 0L

    init {
        contentDescription = "$headline $subline"
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    private fun dp(value: Float) = value * resources.displayMetrics.density

    fun play() {
        started = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // The largest headline that fits on one line.
        var size = sp(60f)
        head.textSize = size
        while (head.measureText(headline) > w - dp(96f) && size > sp(30f)) {
            size -= sp(2f)
            head.textSize = size
        }
        sheen.textSize = size
        headWidth = head.measureText(headline)
        headLeft = (w - headWidth) / 2f
        var offset = 0
        words = headline.split(' ').map { word ->
            val x = headLeft + head.measureText(headline, 0, offset)
            offset += word.length + 1
            word to x
        }
        val headMetrics = head.fontMetrics
        val headHeight = headMetrics.descent - headMetrics.ascent
        val block = mark.textSize + dp(28f) + headHeight + dp(22f) + sub.textSize
        val top = (h - block) / 2f
        markY = top + mark.textSize
        headY = markY + dp(28f) - headMetrics.ascent
        subY = headY + headMetrics.descent + dp(22f) + sub.textSize
        glowY = headY - headHeight / 3f
        glow.shader = RadialGradient(w / 2f, glowY, w * .42f,
            intArrayOf(Color.argb(70, 164, 228, 211), Color.argb(34, 139, 120, 214), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        val band = size * 2.2f
        // The accent colour: white light would not show on white letters.
        sheenShader = LinearGradient(0f, 0f, band, band * .35f,
            intArrayOf(Color.TRANSPARENT, Color.argb(235, 164, 228, 211), Color.argb(255, 200, 245, 232),
                Color.argb(235, 164, 228, 211), Color.TRANSPARENT),
            floatArrayOf(0f, .3f, .5f, .7f, 1f), Shader.TileMode.CLAMP)
        sheen.shader = sheenShader
    }

    /** 0 before [from], 1 after [from] + [length], eased in between. */
    private fun phase(t: Long, from: Long, length: Long): Float {
        val p = ((t - from).toFloat() / length).coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p) * (1f - p)
    }

    override fun onDraw(canvas: Canvas) {
        if (started == 0L) return
        val t = (SystemClock.uptimeMillis() - started).coerceAtMost(DURATION)
        val cx = width / 2f

        // A soft light behind the headline that breathes while the sequence plays.
        val breath = if (motion && t < DURATION) .88f + .12f * sin(t / 650f) else 1f
        glow.alpha = (255 * phase(t, 0, 1400) * breath).toInt()
        // Squashed into a wide ellipse that fades out inside the view, so no edge shows.
        val radius = width * .42f
        val squash = minOf(.5f, minOf(glowY, height - glowY) / radius)
        canvas.save()
        canvas.scale(1f, squash, cx, glowY)
        canvas.drawCircle(cx, glowY, radius, glow)
        canvas.restore()

        val markIn = phase(t, 250, 1100)
        mark.alpha = (255 * markIn).toInt()
        mark.letterSpacing = if (motion) .55f - .3f * markIn else .25f
        canvas.drawText("telum", cx, markY, mark)

        words.forEachIndexed { i, (word, x) ->
            val p = phase(t, 800L + i * 170L, 750)
            head.alpha = (255 * p).toInt()
            val rise = if (motion) (1f - p) * dp(30f) else 0f
            canvas.drawText(word, x, headY + rise, head)
        }

        if (motion) {
            val pass = ((t - 2500).toFloat() / 1300f)
            val shader = sheenShader
            if (pass in 0f..1f && shader != null) {
                val band = sheen.textSize * 2.2f
                sheenMatrix.setTranslate(headLeft - band + (headWidth + band) * pass, headY - sheen.textSize)
                shader.setLocalMatrix(sheenMatrix)
                canvas.drawText(headline, headLeft, headY, sheen)
            }
        }

        val subIn = phase(t, 2900, 900)
        sub.alpha = (255 * subIn).toInt()
        canvas.drawText(subline, cx, subY + if (motion) (1f - subIn) * dp(14f) else 0f, sub)

        if (t < DURATION) postInvalidateOnAnimation()
    }
}
