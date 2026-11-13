package com.retrocam.filmengine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure-Kotlin CPU reference of the COLOUR part of the shader (exposure, contrast, black lift,
 * LUT, saturation, colour shift, vignette). No grain, blur, CA, flash, halation, flare or leak.
 * Used by unit tests and by the golden-image instrumented test. Keep in lock-step with
 * [FilmShader].
 */
object FilmMath {
    const val GAMMA = 2.2f
    const val LUMA_R = 0.2126f
    const val LUMA_G = 0.7152f
    const val LUMA_B = 0.0722f
    internal const val HALATION_TAPS = 12

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

    /** Colour pipeline for one pixel at output position ([u], [v]) of an image with [aspect] = w/h. */
    fun apply(r: Float, g: Float, b: Float, params: FilmParams, lut: Lut, u: Float, v: Float, aspect: Float): Rgb {
        val c = grade(r, g, b, params, lut)
        val m = vignette(params, radius(u, v, aspect))
        return Rgb(clamp01(c.r * m), clamp01(c.g * m), clamp01(c.b * m))
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
