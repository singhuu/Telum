package dev.telum.launcher

import android.content.res.AssetManager
import android.graphics.Typeface

/**
 * The bundled interface typefaces (SIL Open Font License, see assets/fonts). Each file is a
 * variable font, so one file serves every weight. Only the chosen family is read, and each
 * weight is built once.
 */
object Fonts {
    val CHOICES = listOf("manrope", "inter")

    fun name(id: String) = if (id == "inter") "Inter" else "Manrope"

    private val cache = HashMap<String, Typeface>()

    /** [display] picks Inter's large optical size for titles; Manrope has a single design. */
    fun face(assets: AssetManager, id: String, weight: Int, display: Boolean = false): Typeface {
        val inter = id == "inter"
        val key = "$id:$weight:${inter && display}"
        return cache.getOrPut(key) {
            val file = if (inter) "fonts/Inter.ttf" else "fonts/Manrope.ttf"
            val axes = if (inter) "'wght' $weight, 'opsz' ${if (display) 32 else 14}" else "'wght' $weight"
            try {
                Typeface.Builder(assets, file).setFontVariationSettings(axes).setWeight(weight).build()
            } catch (_: Exception) {
                null
            } ?: Typeface.create(Typeface.SANS_SERIF, weight, false)
        }
    }
}
