package com.retrocam.filmengine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure-Kotlin CPU reference of the COLOUR part of the shader (exposure, contrast, black lift,
 * LUT, saturation, colour shift, vignette, filter strength). No grain, blur, CA, flash, halation, flare or leak.
 * Used by unit tests and by the golden-image instrumented test. Keep in lock-step with
 * [FilmShader].
 */
object FilmMath {
    const val GAMMA = 2.2f
    const val LUMA_R = 0.2126f
    const val LUMA_G = 0.7152f
    const val LUMA_B = 0.0722f
    internal const val HALATION_TAPS = 12
    // Flash background falloff tuning (shared with the shader): luma below LO drops fully, above HI not at all.
    const val BG_LUMA_LO = 0.15f
    const val BG_LUMA_HI = 0.45f
    const val BG_RADIAL_MIN = 0.6f

    private fun clamp01(x: Float) = min(1f, max(0f, x))

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    /** Normalised radial distance: 0 at the centre, 1 at the corners. [u], [v] in 0..1. */
    fun radius(u: Float, v: Float, aspect: Float): Float {
        val px = (u - 0.5f) * aspect
        val py = v - 0.5f
        return sqrt(px * px + py * py) / (0.5f * sqrt(aspect * aspect + 1f))
    }

    fun luma(r: Float, g: Float, b: Float): Float = LUMA_R * r + LUMA_G * g + LUMA_B * b

    /** Everything up to and including colour shift (no vignette). Inputs/outputs 0..1 sRGB-ish. */
    fun grade(r: Float, g: Float, b: Float, params: FilmParams, lut: Lut): Rgb {
        var cr = r
        var cg = g
        var cb = b
        val mul = 2f.pow(params.exposureEv)
        if (mul != 1f) {
            cr = clamp01(max(cr, 0f).pow(GAMMA).times(mul).pow(1f / GAMMA))
            cg = clamp01(max(cg, 0f).pow(GAMMA).times(mul).pow(1f / GAMMA))
            cb = clamp01(max(cb, 0f).pow(GAMMA).times(mul).pow(1f / GAMMA))
        }
        val k = params.contrast
        val lift = params.blackLift
        cr = clamp01(lift + ((cr - 0.5f) * k + 0.5f) * (1f - lift))
        cg = clamp01(lift + ((cg - 0.5f) * k + 0.5f) * (1f - lift))
        cb = clamp01(lift + ((cb - 0.5f) * k + 0.5f) * (1f - lift))

        val l = lut.sample(cr, cg, cb)
        val y = luma(l.r, l.g, l.b)
        val s = params.saturation
        val sr = clamp01(y + (l.r - y) * s)
        val sg = clamp01(y + (l.g - y) * s)
        val sb = clamp01(y + (l.b - y) * s)
        val shift = params.colorShift
        return Rgb(clamp01(sr + shift.r), clamp01(sg + shift.g), clamp01(sb + shift.b))
    }

    /** Vignette multiplier (1 = untouched) at radius [r]. */
    fun vignette(params: FilmParams, r: Float): Float {
        if (params.vignetteStrength <= 0f) return 1f
        val edge0 = 0.85f + (0f - 0.85f) * params.vignetteSoftness
        val v = smoothstep(edge0, 1.05f, r)
        return 1f - params.vignetteStrength * v
    }

    /** Multiplier (1 = untouched) of the flash background falloff for an input pixel of [luma] at radius [r]. */
    fun backgroundKeep(flash: FlashLook?, luma: Float, r: Float): Float {
        val k = flash?.backgroundDrop?.let { clamp01(it) } ?: 0f
        if (k <= 0f) return 1f
        val drop = k * (1f - smoothstep(BG_LUMA_LO, BG_LUMA_HI, luma)) * (BG_RADIAL_MIN + (1f - BG_RADIAL_MIN) * clamp01(r))
        return 1f - clamp01(drop)
    }

    /** Filter strength limited to 0..1 (NaN counts as full strength). */
    fun clampStrength(strength: Float): Float = if (strength.isNaN()) 1f else clamp01(strength)

    /** Colour pipeline for one pixel at output position ([u], [v]) of an image with [aspect] = w/h. */
    fun apply(r: Float, g: Float, b: Float, params: FilmParams, lut: Lut, u: Float, v: Float, aspect: Float): Rgb {
        val rad = radius(u, v, aspect)
        val keep = backgroundKeep(params.flash, luma(r, g, b), rad)
        val c = grade(r * keep, g * keep, b * keep, params, lut)
        val m = vignette(params, rad)
        val fr = clamp01(c.r * m)
        val fg = clamp01(c.g * m)
        val fb = clamp01(c.b * m)
        val k = clampStrength(params.strength)
        if (k >= 1f) return Rgb(fr, fg, fb)
        // mix(input, film, strength), exactly the shader's final step
        return Rgb(r + (fr - r) * k, g + (fg - g) * k, b + (fb - b) * k)
    }

    /** Flash light colour relative to 6500 K (white), times the optional gel. */
    internal fun flashTint(flash: FlashLook): FloatArray {
        val base = kelvinToRgb(flash.kelvin)
        val ref = kelvinToRgb(6500f)
        val gel = flash.gel
        return floatArrayOf(
            min(2f, base[0] / ref[0] * (gel?.r ?: 1f)),
            min(2f, base[1] / ref[1] * (gel?.g ?: 1f)),
            min(2f, base[2] / ref[2] * (gel?.b ?: 1f)),
        )
    }

    /** Black-body colour approximation (Tanner Helland), components 0..1. */
    internal fun kelvinToRgb(kelvin: Float): FloatArray {
        val t = kelvin.coerceIn(1000f, 40000f) / 100f
        val r = if (t <= 66f) 255f else 329.698727446f * (t - 60f).pow(-0.1332047592f)
        val g = if (t <= 66f) 99.4708025861f * kotlin.math.ln(t) - 161.1195681661f
        else 288.1221695283f * (t - 60f).pow(-0.0755148492f)
        val b = if (t >= 66f) 255f else if (t <= 19f) 0f else 138.5177312231f * kotlin.math.ln(t - 10f) - 305.0447927307f
        return floatArrayOf(
            (r / 255f).coerceIn(0.02f, 1f),
            (g / 255f).coerceIn(0.02f, 1f),
            (b / 255f).coerceIn(0.02f, 1f),
        )
    }
}
