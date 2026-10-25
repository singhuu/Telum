package dev.telum.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line icons drawn in code on a 24-unit grid, so they stay sharp at any size and cost no
 * resources to load.
 */
object Glyphs {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val r = RectF()

    fun draw(cv: Canvas, name: String, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 24f
        cv.save()
        cv.translate(cx - size / 2f, cy - size / 2f)
        cv.scale(s, s)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.shader = null
        when (name) {
            "search" -> {
                cv.drawCircle(10.5f, 10.5f, 6.5f, p)
                cv.drawLine(15.5f, 15.5f, 20.5f, 20.5f, p)
            }
            "mic" -> {
                r.set(9f, 3f, 15f, 15f); cv.drawRoundRect(r, 3f, 3f, p)
                path.reset(); path.moveTo(5.5f, 11.5f); path.cubicTo(5.5f, 19.5f, 18.5f, 19.5f, 18.5f, 11.5f); cv.drawPath(path, p)
                cv.drawLine(12f, 18f, 12f, 21f, p)
            }
            "controls" -> {
                r.set(3f, 4.5f, 21f, 10.5f); cv.drawRoundRect(r, 3f, 3f, p)
                r.set(3f, 13.5f, 21f, 19.5f); cv.drawRoundRect(r, 3f, 3f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(17.5f, 7.5f, 1.9f, p)
                cv.drawCircle(6.5f, 16.5f, 1.9f, p)
            }
            "bell" -> {
                path.reset()
                path.moveTo(6f, 16.5f); path.lineTo(6f, 10.5f)
                path.cubicTo(6f, 6.5f, 8.5f, 4f, 12f, 4f); path.cubicTo(15.5f, 4f, 18f, 6.5f, 18f, 10.5f)
                path.lineTo(18f, 16.5f); path.lineTo(19.5f, 18f); path.lineTo(4.5f, 18f); path.close()
                cv.drawPath(path, p)
                cv.drawLine(10.5f, 21f, 13.5f, 21f, p)
            }
            "moon" -> {
                path.reset()
                path.moveTo(19.5f, 14.5f)
                path.cubicTo(18f, 18.2f, 14.6f, 20.5f, 11f, 20f)
                path.cubicTo(6.5f, 19.3f, 3.5f, 15f, 4.2f, 10.5f)
                path.cubicTo(4.8f, 7f, 7f, 4.6f, 9.8f, 3.8f)
                path.cubicTo(8.3f, 8.8f, 13.6f, 15.4f, 19.5f, 14.5f)
                path.close(); cv.drawPath(path, p)
            }
            "wifi" -> {
                r.set(2f, 6f, 22f, 26f); cv.drawArc(r, 225f, 90f, false, p)
                r.set(5.5f, 9.5f, 18.5f, 22.5f); cv.drawArc(r, 225f, 90f, false, p)
                r.set(9f, 13f, 15f, 19f); cv.drawArc(r, 225f, 90f, false, p)
                p.style = Paint.Style.FILL; cv.drawCircle(12f, 19.5f, 1.4f, p)
            }
            "display" -> {
                r.set(3f, 4.5f, 21f, 16f); cv.drawRoundRect(r, 2f, 2f, p)
                cv.drawLine(8.5f, 20f, 15.5f, 20f, p)
                cv.drawLine(12f, 16f, 12f, 20f, p)
            }
            "remote" -> {
                r.set(8f, 2.5f, 16f, 21.5f); cv.drawRoundRect(r, 4f, 4f, p)
                cv.drawCircle(12f, 8f, 2f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(12f, 13.5f, 1f, p); cv.drawCircle(12f, 17f, 1f, p)
            }
            "grid" -> {
                for (i in 0..1) for (j in 0..1) {
                    r.set(4f + i * 9f, 4f + j * 9f, 11f + i * 9f, 11f + j * 9f); cv.drawRoundRect(r, 2f, 2f, p)
                }
            }
            "sliders" -> {
                cv.drawLine(4f, 7f, 20f, 7f, p); cv.drawLine(4f, 17f, 20f, 17f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(15f, 7f, 2.6f, p); cv.drawCircle(9f, 17f, 2.6f, p)
            }
            "eye-off" -> {
                path.reset(); path.moveTo(2.5f, 12f); path.cubicTo(6f, 5.5f, 18f, 5.5f, 21.5f, 12f)
                path.cubicTo(18f, 18.5f, 6f, 18.5f, 2.5f, 12f); cv.drawPath(path, p)
                cv.drawCircle(12f, 12f, 3f, p)
                cv.drawLine(4f, 20f, 20f, 4f, p)
            }
            "eye" -> {
                path.reset(); path.moveTo(2.5f, 12f); path.cubicTo(6f, 5.5f, 18f, 5.5f, 21.5f, 12f)
                path.cubicTo(18f, 18.5f, 6f, 18.5f, 2.5f, 12f); cv.drawPath(path, p)
                cv.drawCircle(12f, 12f, 3f, p)
            }
            "move" -> {
                cv.drawLine(12f, 3f, 12f, 21f, p); cv.drawLine(3f, 12f, 21f, 12f, p)
                arrowHead(cv, 12f, 3f, 0f); arrowHead(cv, 12f, 21f, 180f)
                arrowHead(cv, 3f, 12f, 270f); arrowHead(cv, 21f, 12f, 90f)
            }
            "info" -> {
                cv.drawCircle(12f, 12f, 9f, p)
                cv.drawLine(12f, 11f, 12f, 16.5f, p)
                p.style = Paint.Style.FILL; cv.drawCircle(12f, 7.6f, 1.2f, p)
            }
            "home" -> {
                path.reset(); path.moveTo(4f, 11f); path.lineTo(12f, 4f); path.lineTo(20f, 11f)
                cv.drawPath(path, p)
                path.reset(); path.moveTo(6.5f, 9.5f); path.lineTo(6.5f, 20f); path.lineTo(17.5f, 20f); path.lineTo(17.5f, 9.5f)
                cv.drawPath(path, p)
            }
            "check" -> {
                path.reset(); path.moveTo(5f, 12.5f); path.lineTo(10f, 17.5f); path.lineTo(19f, 7f); cv.drawPath(path, p)
            }
            "chevron" -> {
                path.reset(); path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f); cv.drawPath(path, p)
            }
            "back" -> {
                path.reset(); path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f); cv.drawPath(path, p)
            }
            "power" -> {
                r.set(4f, 4.5f, 20f, 20.5f); cv.drawArc(r, -60f, 300f, false, p)
                cv.drawLine(12f, 3f, 12f, 11f, p)
            }
            "store" -> {
                path.reset(); path.moveTo(4f, 8f); path.lineTo(20f, 8f); path.lineTo(18.5f, 20f); path.lineTo(5.5f, 20f); path.close()
                cv.drawPath(path, p)
                r.set(8.5f, 3.5f, 15.5f, 11f); cv.drawArc(r, 180f, 180f, false, p)
            }
            "restart" -> {
                r.set(4.5f, 4.5f, 19.5f, 19.5f); cv.drawArc(r, -80f, 300f, false, p)
                arrowHead(cv, 13.5f, 4.6f, 90f)
            }
            "image" -> {
                r.set(3f, 5f, 21f, 19f); cv.drawRoundRect(r, 2.5f, 2.5f, p)
                path.reset(); path.moveTo(3.5f, 17f); path.lineTo(9f, 11.5f); path.lineTo(13f, 15f); path.lineTo(15.5f, 13f); path.lineTo(20.5f, 17.5f)
                cv.drawPath(path, p)
                p.style = Paint.Style.FILL; cv.drawCircle(15.5f, 9f, 1.6f, p)
            }
            "motion" -> {
                path.reset(); path.moveTo(3f, 12f)
                path.cubicTo(6f, 5f, 9f, 5f, 12f, 12f); path.cubicTo(15f, 19f, 18f, 19f, 21f, 12f)
                cv.drawPath(path, p)
            }
            "label" -> {
                cv.drawLine(4f, 7f, 20f, 7f, p); cv.drawLine(12f, 7f, 12f, 19f, p)
            }
            "sound" -> {
                path.reset(); path.moveTo(4f, 9.5f); path.lineTo(7.5f, 9.5f); path.lineTo(12f, 5.5f)
                path.lineTo(12f, 18.5f); path.lineTo(7.5f, 14.5f); path.lineTo(4f, 14.5f); path.close()
                cv.drawPath(path, p)
                r.set(10f, 8.5f, 16f, 15.5f); cv.drawArc(r, -50f, 100f, false, p)
                r.set(10f, 5.5f, 20f, 18.5f); cv.drawArc(r, -50f, 100f, false, p)
            }
            "play" -> {
                cv.drawCircle(12f, 12f, 8.5f, p)
                path.reset(); path.moveTo(10f, 8.5f); path.lineTo(15.5f, 12f); path.lineTo(10f, 15.5f); path.close()
                p.style = Paint.Style.FILL; cv.drawPath(path, p); p.style = Paint.Style.STROKE
            }
            "music" -> {
                cv.drawLine(9f, 17f, 9f, 5f, p); cv.drawLine(9f, 5f, 19f, 3.5f, p); cv.drawLine(19f, 3.5f, 19f, 15.5f, p)
                p.style = Paint.Style.FILL; cv.drawCircle(6.6f, 17.2f, 2.6f, p); cv.drawCircle(16.6f, 15.7f, 2.6f, p); p.style = Paint.Style.STROKE
            }
            "media-play" -> {
                path.reset(); path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close()
                p.style = Paint.Style.FILL; cv.drawPath(path, p); p.style = Paint.Style.STROKE
            }
            "media-pause" -> {
                p.style = Paint.Style.FILL
                r.set(6.5f, 5f, 10.5f, 19f); cv.drawRoundRect(r, 1f, 1f, p)
                r.set(13.5f, 5f, 17.5f, 19f); cv.drawRoundRect(r, 1f, 1f, p)
                p.style = Paint.Style.STROKE
            }
            "media-next", "media-prev" -> {
                if (name == "media-prev") { cv.save(); cv.scale(-1f, 1f, 12f, 12f) }
                path.reset(); path.moveTo(5f, 6f); path.lineTo(14f, 12f); path.lineTo(5f, 18f); path.close()
                p.style = Paint.Style.FILL; cv.drawPath(path, p)
                r.set(15.5f, 6f, 18.5f, 18f); cv.drawRoundRect(r, 1f, 1f, p); p.style = Paint.Style.STROKE
                if (name == "media-prev") cv.restore()
            }
            "rows" -> {
                r.set(3.5f, 5f, 20.5f, 9.5f); cv.drawRoundRect(r, 2f, 2f, p)
                r.set(3.5f, 14.5f, 20.5f, 19f); cv.drawRoundRect(r, 2f, 2f, p)
            }
            "type" -> {
                path.reset(); path.moveTo(3f, 19f); path.lineTo(8.5f, 5f); path.lineTo(14f, 19f); cv.drawPath(path, p)
                cv.drawLine(5.2f, 14f, 11.8f, 14f, p)
                cv.drawCircle(18f, 16.2f, 2.6f, p)
                cv.drawLine(20.6f, 12.5f, 20.6f, 19f, p)
            }
            "clock" -> {
                cv.drawCircle(12f, 12f, 8.5f, p)
                path.reset(); path.moveTo(12f, 7f); path.lineTo(12f, 12f); path.lineTo(15.5f, 14f); cv.drawPath(path, p)
            }
            "thermo" -> {
                r.set(10f, 3f, 14f, 15f); cv.drawRoundRect(r, 2f, 2f, p)
                cv.drawCircle(12f, 17.5f, 3.5f, p)
                p.style = Paint.Style.FILL; cv.drawCircle(12f, 17.5f, 1.6f, p)
            }
            "pin" -> {
                path.reset(); path.moveTo(12f, 21f)
                path.cubicTo(7f, 15.5f, 5f, 12.5f, 5f, 9.5f); path.cubicTo(5f, 5.5f, 8f, 3f, 12f, 3f)
                path.cubicTo(16f, 3f, 19f, 5.5f, 19f, 9.5f); path.cubicTo(19f, 12.5f, 17f, 15.5f, 12f, 21f); path.close()
                cv.drawPath(path, p)
                cv.drawCircle(12f, 9.5f, 2.5f, p)
            }
            "sun" -> sun(cv, 12f, 12f, 1f)
            "cloud" -> cloud(cv, 12f, 13f, 1f)
            "partly" -> { sun(cv, 9f, 9f, 0.62f); cloud(cv, 13.5f, 14.5f, 0.78f) }
            "partly-night" -> { moonSmall(cv, 9f, 8.5f); cloud(cv, 13.5f, 14.5f, 0.78f) }
            "rain" -> {
                cloud(cv, 12f, 10f, 0.9f)
                cv.drawLine(8f, 17f, 7f, 20.5f, p); cv.drawLine(12f, 17f, 11f, 20.5f, p); cv.drawLine(16f, 17f, 15f, 20.5f, p)
            }
            "snow" -> {
                cloud(cv, 12f, 10f, 0.9f)
                p.style = Paint.Style.FILL
                cv.drawCircle(8f, 18.5f, 1.15f, p); cv.drawCircle(12f, 20f, 1.15f, p); cv.drawCircle(16f, 18.5f, 1.15f, p)
            }
            "storm" -> {
                cloud(cv, 12f, 10f, 0.9f)
                path.reset(); path.moveTo(13f, 14.5f); path.lineTo(10f, 18.5f); path.lineTo(13f, 18.5f); path.lineTo(11f, 22f)
                cv.drawPath(path, p)
            }
            "fog" -> {
                cv.drawLine(4f, 9f, 20f, 9f, p); cv.drawLine(6f, 13f, 18f, 13f, p); cv.drawLine(4f, 17f, 20f, 17f, p)
            }
            "night" -> moonSmall(cv, 12f, 12f, 1.25f)
        }
        cv.restore()
    }

    private fun arrowHead(cv: Canvas, x: Float, y: Float, deg: Float) {
        cv.save(); cv.rotate(deg, x, y)
        cv.drawLine(x, y, x - 2.8f, y + 2.8f, p); cv.drawLine(x, y, x + 2.8f, y + 2.8f, p)
        cv.restore()
    }

    private fun sun(cv: Canvas, cx: Float, cy: Float, k: Float) {
        cv.drawCircle(cx, cy, 4f * k, p)
        for (i in 0 until 8) {
            val a = (i * PI / 4).toFloat()
            cv.drawLine(cx + cos(a) * 7f * k, cy + sin(a) * 7f * k, cx + cos(a) * 9.5f * k, cy + sin(a) * 9.5f * k, p)
        }
    }

    private fun moonSmall(cv: Canvas, cx: Float, cy: Float, k: Float = 0.6f) {
        cv.save(); cv.translate(cx - 12f * k, cy - 12f * k); cv.scale(k, k)
        path.reset()
        path.moveTo(19.5f, 14.5f)
        path.cubicTo(18f, 18.2f, 14.6f, 20.5f, 11f, 20f)
        path.cubicTo(6.5f, 19.3f, 3.5f, 15f, 4.2f, 10.5f)
        path.cubicTo(4.8f, 7f, 7f, 4.6f, 9.8f, 3.8f)
        path.cubicTo(8.3f, 8.8f, 13.6f, 15.4f, 19.5f, 14.5f)
        path.close(); cv.drawPath(path, p)
        cv.restore()
    }

    private fun cloud(cv: Canvas, cx: Float, cy: Float, k: Float) {
        cv.save(); cv.translate(cx, cy); cv.scale(k, k)
        path.reset()
        path.moveTo(-6.5f, 5f)
        path.cubicTo(-10f, 5f, -10.5f, -0.5f, -6.5f, -1f)
        path.cubicTo(-6f, -5.5f, 0.5f, -7f, 2.5f, -3f)
        path.cubicTo(6f, -4.5f, 9f, -1.5f, 8f, 1.5f)
        path.cubicTo(10.5f, 2f, 10f, 5f, 7.5f, 5f)
        path.close()
        cv.drawPath(path, p)
        cv.restore()
    }

    /** A filled gear used for the Settings tile, with a hole punched in [hole] colour. */
    fun gear(cv: Canvas, cx: Float, cy: Float, size: Float, color: Int, hole: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val g = Path()
        val teeth = 10
        val ro = size * 0.5f
        val ri = size * 0.4f
        for (i in 0 until teeth * 2) {
            val a0 = (i * PI / teeth).toFloat()
            val a1 = ((i + 1) * PI / teeth).toFloat()
            val rr = if (i % 2 == 0) ro else ri
            val x0 = cx + cos(a0 + 0.06f) * rr
            val y0 = cy + sin(a0 + 0.06f) * rr
            if (i == 0) g.moveTo(x0, y0) else g.lineTo(x0, y0)
            g.lineTo(cx + cos(a1 - 0.06f) * rr, cy + sin(a1 - 0.06f) * rr)
        }
        g.close()
        g.addCircle(cx, cy, size * 0.17f, Path.Direction.CCW)
        g.fillType = Path.FillType.EVEN_ODD
        val shadow = Paint(fill).apply { this.color = 0x33000000 }
        cv.save(); cv.translate(0f, size * 0.03f); cv.drawPath(g, shadow); cv.restore()
        cv.drawPath(g, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = hole; style = Paint.Style.STROKE; strokeWidth = size * 0.035f }
        cv.drawCircle(cx, cy, size * 0.28f, ring)
    }
}
