package dev.telum.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Telum's intro: the naked-eye sky around the Arrow on true black, a line drawn along the Arrow,
 * then one slow push to Telum (gamma Sagittae) until the star is the core of the Telum mark. Drawn
 * with OpenGL ES 3.0 from the shaders in
 * assets/intro, the same files the WebGL prototype runs (launcher/prototype/telum-intro.html);
 * the whole timeline lives in those shaders.
 *
 * The surface sits above the window and is translucent, so the intro dissolves straight into the
 * page underneath. [onReveal] runs as the dissolve starts and [onDone] when nothing is left to
 * draw; the owner then removes the view, which frees its GL context. If GL fails, both run at once.
 */
@SuppressLint("ViewConstructor") // Created by MainActivity with its callbacks, never from XML.
class IntroView(
    context: Context,
    private val motion: Boolean,
    private val sound: IntroSound?,
    private val onReveal: () -> Unit,
    private val onDone: () -> Unit,
) : GLSurfaceView(context) {

    companion object {
        /** FULL_END, CALM_END, DISSOLVE and STAR_COUNT in common.glsl. */
        const val FULL_LENGTH = 7.0f
        const val CALM_LENGTH = 3.0f
        private const val DISSOLVE = 0.5f
        private const val SKIP_FADE = 0.35f
        private const val STAR_COUNT = 30
        private const val HEAD = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"

        fun supported(context: Context): Boolean =
            context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }

    private val length = if (motion) FULL_LENGTH else CALM_LENGTH
    private val face: Typeface = Fonts.face(context.assets, "manrope", 800)
    private val renderer = Renderer()
    private var finished = false
    private var revealSent = false
    private val failsafe = Runnable { finish("timed out") }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        isFocusable = false
        contentDescription = "Telum intro"
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        // Whatever happens to GL, Home must appear.
        postDelayed(failsafe, ((length + 5f) * 1000).toLong())
    }

    /** A remote press: fade out now, sound and picture together. */
    fun skip() {
        sound?.fadeOut((SKIP_FADE * 1000).toLong())
        queueEvent { renderer.skip() }
    }

    /** Ends the intro at once, as when Telum leaves the screen while it plays. */
    fun cancel() = finish(null)

    private fun finish(problem: String?) {
        if (finished) return
        finished = true
        removeCallbacks(failsafe)
        sound?.stop()
        if (problem != null) Log.w("Telum", "intro: $problem")
        reveal()
        onDone()
    }

    /** Main thread only, and once: the GL thread's request can arrive after a cancel. */
    private fun reveal() {
        if (revealSent) return
        revealSent = true
        onReveal()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(failsafe)
        super.onDetachedFromWindow()
    }

    /**
     * The mark (Telum above a slim notched dart, as in the app icon) over the wordmark, centred on the
     * screen as one group, for a render [height] pixels tall: Telum's place and size, the mark's
     * unit, and the wordmark's texture and place. See lockup() in the prototype.
     */
    private class Lockup(val mark: FloatArray, val word: FloatArray, val bitmap: Bitmap)

    private fun lockup(height: Int): Lockup {
        val h = height.toFloat()
        // The mark in the icon's 96-unit grid: Telum's radius is 6.5, and the mark reaches 6.5
        // units above Telum and 61.5 below.
        val unit = 0.0035f
        val core = 6.5f * unit
        val above = 6.5f * unit
        val below = 61.5f * unit
        val ascender = 0.1024f
        val gap = ascender * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 200f }
        val bounds = Rect()
        paint.getTextBounds("l", 0, 1, bounds)
        paint.textSize = ascender * h / (-bounds.top / 200f)
        paint.getTextBounds("telum", 0, 5, bounds)
        val inkW = bounds.width() / h
        val total = above + below + gap + ascender
        val telumY = total / 2f - above
        val baseline = -total / 2f
        val left = -inkW / 2f
        // Sharp letters in red, a soft copy in green and a wide glow in blue. Skia's blur radius
        // is (sigma - 0.5) / 0.57735, which matches the prototype's CSS blur(4px) and blur(18px).
        val pad = 48
        val bitmap = Bitmap.createBitmap(bounds.width() + 2 * pad, bounds.height() + 2 * pad, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val canvas = Canvas(bitmap)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        val x = pad - bounds.left.toFloat()
        val y = pad - bounds.top.toFloat()
        for ((color, sigma) in listOf(Color.BLUE to 18f, Color.GREEN to 4f, Color.RED to 0f)) {
            paint.color = color
            paint.maskFilter = if (sigma > 0f) BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawText("telum", x, y, paint)
        }
        val top = baseline - bounds.top / h + pad / h
        return Lockup(floatArrayOf(0f, telumY, core, unit),
            floatArrayOf(left - pad / h, top - bitmap.height / h, bitmap.width / h, bitmap.height / h), bitmap)
    }

    private inner class Renderer : GLSurfaceView.Renderer {
        private var revealed = false
        private var done = false
        private var failed = false
        private var scene = 0
        private var stars = 0
        private var wordTex = 0
        private var width = 0
        private var height = 0
        private var lock: Lockup? = null
        private var start = 0L
        private var last = 0L
        private var skipAt = -1f
        private var now = 0f
        private val frames = ArrayList<Float>(600)

        fun skip() {
            if (skipAt < 0f && now < length) skipAt = now
        }

        private fun fail(problem: String) {
            failed = true
            post { finish(problem) }
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                val common = source("common.glsl")
                scene = program(common, "scene.vert", "scene.frag")
                stars = program(common, "stars.vert", "stars.frag")
                val vao = IntArray(1)
                GLES30.glGenVertexArrays(1, vao, 0)
                GLES30.glBindVertexArray(vao[0])
                val error = GLES30.glGetError()
                if (error != GLES30.GL_NO_ERROR) throw IllegalStateException("GL error $error preparing the intro")
            } catch (e: Exception) {
                fail(e.message ?: e.toString())
            }
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            width = w
            height = h
            GLES30.glViewport(0, 0, w, h)
            if (failed) return
            try {
                val made = lockup(h)
                lock = made
                if (wordTex == 0) wordTex = texture()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, wordTex)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, made.bitmap, 0)
                made.bitmap.recycle()
            } catch (e: Exception) {
                fail(e.message ?: e.toString())
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            val clock = SystemClock.elapsedRealtimeNanos()
            if (start == 0L) {
                start = clock
                last = clock
                // The hum starts with the first frame; the asset follows the same timeline.
                if (sound != null) post { if (!finished) sound.start() }
            }
            frames += (clock - last) / 1e6f
            last = clock
            now = (clock - start) / 1e9f
            val t = now
            val lock = lock
            if (failed || lock == null) {
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                return
            }
            val revealAt = if (skipAt >= 0f) skipAt else length - DISSOLVE
            val doneAt = if (skipAt >= 0f) minOf(skipAt + SKIP_FADE, length) else length
            if (!revealed && t >= revealAt) {
                revealed = true
                post { reveal() }
            }
            if (t >= doneAt) {
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                if (!done) {
                    done = true
                    report(t)
                    post { finish(null) }
                }
                return
            }

            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glUseProgram(scene)
            common(scene, t, lock)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, wordTex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(scene, "uWord"), 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

            if (motion) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
                GLES30.glUseProgram(stars)
                common(stars, t, lock)
                GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, STAR_COUNT)
            }
        }

        private fun common(program: Int, t: Float, lock: Lockup) {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTime"), t)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMotion"), if (motion) 1f else 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSkip"), skipAt)
            GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "uMark"), 1, lock.mark, 0)
            GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "uWordRect"), 1, lock.word, 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uRes"), width.toFloat(), height.toFloat())
        }

        /** Frame pacing for the record: the intro runs on its own surface, outside gfxinfo. */
        private fun report(t: Float) {
            val gaps = frames.drop(1).sorted()
            if (gaps.isEmpty()) return
            fun at(q: Float) = gaps[((gaps.size - 1) * q).toInt()]
            Log.i("Telum", "intro: %d frames in %.2f s at %dx%d, frame time p50 %.1f ms, p90 %.1f ms, max %.1f ms, %d over 20 ms%s".format(
                gaps.size + 1, t, width, height, at(.5f), at(.9f), gaps.last(), gaps.count { it > 20f },
                if (skipAt >= 0f) ", skipped at %.2f s".format(skipAt) else ""))
        }

        private fun source(name: String) =
            context.assets.open("intro/$name").bufferedReader().use { it.readText() }

        private fun shader(type: Int, code: String, name: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, code)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("$name: ${GLES30.glGetShaderInfoLog(s)}")
            return s
        }

        private fun program(common: String, vertex: String, fragment: String): Int {
            val p = GLES30.glCreateProgram()
            val v = shader(GLES30.GL_VERTEX_SHADER, HEAD + common + "\n" + source(vertex), vertex)
            val f = shader(GLES30.GL_FRAGMENT_SHADER, HEAD + common + "\n" + source(fragment), fragment)
            GLES30.glAttachShader(p, v)
            GLES30.glAttachShader(p, f)
            GLES30.glLinkProgram(p)
            GLES30.glDeleteShader(v)
            GLES30.glDeleteShader(f)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("$vertex + $fragment: ${GLES30.glGetProgramInfoLog(p)}")
            return p
        }

        private fun texture(target: Int = GLES30.GL_TEXTURE_2D): Int {
            val id = IntArray(1)
            GLES30.glGenTextures(1, id, 0)
            GLES30.glBindTexture(target, id[0])
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return id[0]
        }
    }
}
