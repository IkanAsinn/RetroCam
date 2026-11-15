package com.retrocam.filmengine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * "Film flash": a simulated on-camera flash applied only when the photo is developed (capture path). Pure Kotlin so the
 * model is unit-testable; [RelightMath.apply] is the CPU reference of the shader's relight stage and the constants below
 * are interpolated into the shader source, so both stay in lock-step.
 *
 * Model (linear light, gamma 2.2 like the rest of the pipeline):
 *  1. A small, edge-aware-blurred copy of the frame ([IllumMap]) estimates the AMBIENT illumination per pixel.
 *  2. Reflectance (albedo) = linear colour / illumination (Retinex), clamped.
 *  3. The flash light is a broad inverse-square-like falloff `1 / (1 + (d/R)^2)` centred on the subject
 *     ([FlashSubject]: faces when found, otherwise a gentle spot slightly above the frame centre), times the natural
 *     corner fall-off of a small flash's coverage. No circle edge, no hotspot.
 *  4. out = ambient * (1 - ambientDrop * (1 - light)) + strength * light * albedo * flashColour
 *     (shadows are filled because the flash term follows reflectance, not the shadowed pixel value).
 *  5. A hue-preserving soft shoulder rolls highlights off so skin and white objects do not clip flat.
 */
data class FaceBox(val cx: Float, val cy: Float, val size: Float) // 0..1 of the frame, y down; size = face height / frame height

/** Where the flash light is centred, in 0..1 UV (y down); [radius] in units of frame height. */
data class FlashSubject(val cx: Float, val cy: Float, val radius: Float, val faceCount: Int) {
    companion object {
        /** No face found: a wide, gentle falloff centred slightly above the frame centre. */
        val DEFAULT = FlashSubject(0.5f, 0.44f, 0.95f, 0)
        private const val FACE_PULL = 0.65f   // how far the light centre moves from the frame centre toward the faces
        private const val MIN_RADIUS = 0.8f
        private const val MAX_RADIUS = 1.25f
        private const val RADIUS_PER_FACE = 2.8f

        fun fromFaces(faces: List<FaceBox>): FlashSubject {
            val usable = faces.filter { it.size > 0f && it.cx in 0f..1f && it.cy in 0f..1f }
            if (usable.isEmpty()) return DEFAULT
            var wsum = 0f
            var fx = 0f
            var fy = 0f
            var biggest = 0f
            for (f in usable) {
                val w = f.size * f.size
                wsum += w
                fx += f.cx * w
                fy += f.cy * w
                biggest = max(biggest, f.size)
            }
            fx /= wsum
            fy /= wsum
            return FlashSubject(
                cx = 0.5f + (fx - 0.5f) * FACE_PULL,
                cy = 0.5f + (fy - 0.5f) * FACE_PULL,
                radius = (biggest * RADIUS_PER_FACE).coerceIn(MIN_RADIUS, MAX_RADIUS),
                faceCount = usable.size,
            )
        }
    }
}

/**
 * Per-profile film-flash look (from the profile's flash spec) plus the per-shot analysis result.
 * [strength] is the BASE flash amount; [ambientLuma] (mean linear luminance of the scene) scales it down for bright scenes.
 */
data class RelightLook(
    val kelvin: Float,
    val gel: Rgb? = null,
    val strength: Float,
    val ambientDrop: Float,      // 0..1: how far ambient light drops where the flash does not reach
    val reach: Float = 1f,       // multiplier of the subject radius: < 1 = punchier falloff
    val subject: FlashSubject = FlashSubject.DEFAULT,
    val ambientLuma: Float = 0.1f,
)

/** Small ambient-illumination map: linear RGB floats, [width] x [height], row 0 = image top. */
class IllumMap(val width: Int, val height: Int, val linear: FloatArray) {
    init {
        require(linear.size == width * height * 3)
    }

    /** Mean linear luminance. */
    val meanLuma: Float
        get() {
            var s = 0.0
            for (i in 0 until width * height) {
                s += FilmMath.luma(linear[i * 3], linear[i * 3 + 1], linear[i * 3 + 2])
            }
            return (s / (width * height)).toFloat()
        }

    /** RGB8 bytes (gamma encoded) for the GL texture, row 0 first. */
    fun toGammaBytes(): ByteArray {
        val out = ByteArray(linear.size)
        for (i in linear.indices) {
            out[i] = (linear[i].coerceIn(0f, 1f).pow(1f / FilmMath.GAMMA) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }
}

object IllumMapper {
    const val LONG_SIDE = 96
    private const val SPATIAL_SIGMA = 0.12f   // of the long side
    private const val RANGE_SIGMA = 0.8f      // in ln(luminance) units: stronger edges are not blurred across
    private const val STEP = 2

    /**
     * [argb] w*h pixels (sRGB). Returns a map of [longSide] px on the long edge: box downscale in linear light, then an
     * edge-aware blur. [mean] is the mean linear luminance of the unblurred downscale.
     */
    fun fromArgb(argb: IntArray, w: Int, h: Int, longSide: Int = LONG_SIDE): IllumMap {
        require(w > 0 && h > 0 && argb.size >= w * h)
        val k = min(1f, longSide.toFloat() / max(w, h))
        val dw = max(1, (w * k).toInt())
        val dh = max(1, (h * k).toInt())
        val small = FloatArray(dw * dh * 3)
        for (y in 0 until dh) {
            val y0 = y * h / dh
            val y1 = max(y0 + 1, (y + 1) * h / dh)
            for (x in 0 until dw) {
                val x0 = x * w / dw
                val x1 = max(x0 + 1, (x + 1) * w / dw)
                var r = 0f
                var g = 0f
                var b = 0f
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = argb[yy * w + xx]
                    r += LIN[(p shr 16) and 0xFF]
                    g += LIN[(p shr 8) and 0xFF]
                    b += LIN[p and 0xFF]
                }
                val n = ((y1 - y0) * (x1 - x0)).toFloat()
                val o = (y * dw + x) * 3
                small[o] = r / n
                small[o + 1] = g / n
                small[o + 2] = b / n
            }
        }
        return IllumMap(dw, dh, bilateral(small, dw, dh))
    }

    private val LIN = FloatArray(256) { (it / 255f).pow(FilmMath.GAMMA) }

    private fun bilateral(src: FloatArray, w: Int, h: Int): FloatArray {
        val sigma = max(1f, SPATIAL_SIGMA * max(w, h))
        val rad = (2f * sigma).toInt()
        val logL = FloatArray(w * h) { ln(max(FilmMath.luma(src[it * 3], src[it * 3 + 1], src[it * 3 + 2]), 0.004f)) }
        val inv2s = 1f / (2f * sigma * sigma)
        val inv2r = 1f / (2f * RANGE_SIGMA * RANGE_SIGMA)
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            val c = logL[y * w + x]
            var ws = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            var yy = max(0, y - rad)
            while (yy <= min(h - 1, y + rad)) {
                var xx = max(0, x - rad)
                while (xx <= min(w - 1, x + rad)) {
                    val dx = (xx - x).toFloat()
                    val dy = (yy - y).toFloat()
                    val dl = logL[yy * w + xx] - c
                    val wt = exp(-(dx * dx + dy * dy) * inv2s - dl * dl * inv2r)
                    val o = (yy * w + xx) * 3
                    ws += wt
                    r += src[o] * wt
                    g += src[o + 1] * wt
                    b += src[o + 2] * wt
                    xx += STEP
                }
                yy += STEP
            }
            val o = (y * w + x) * 3
            out[o] = r / ws
            out[o + 1] = g / ws
            out[o + 2] = b / ws
        }
        return out
    }
}

object RelightMath {
    // ---- tuning (shared with the shader) ----
    const val ILLUM_FLOOR = 0.05f      // illumination below this counts as this (no divide-by-zero, no night-time blow-up)
    const val CHROMA_KEEP = 1.0f      // 1 = per-channel illumination (full ambient white-balance pull), 0 = luminance only
    const val ALBEDO_MAX = 1.7f
    const val COVER_DROP = 0.3f        // corner fall-off of the small flash's coverage
    const val SHOULDER_KNEE = 0.5f    // highlight roll-off starts here (linear)
    const val ROLL_DESAT = 0.4f        // rolled-off highlights lose this much saturation
    const val MIN_RADIUS = 0.25f
    // adaptation to scene brightness: mean linear luminance LO or darker = full flash, HI or brighter = gentle fill
    const val ADAPT_LO = 0.02f
    const val ADAPT_HI = 0.30f
    const val ADAPT_BRIGHT_STRENGTH = 0.3f
    const val ADAPT_BRIGHT_DROP = 0.35f

    private fun clamp01(x: Float) = min(1f, max(0f, x))

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    /** 1 for dark scenes down to [ADAPT_BRIGHT_STRENGTH] for bright ones. */
    fun strengthAdapt(meanLuma: Float): Float =
        1f + (ADAPT_BRIGHT_STRENGTH - 1f) * smoothstep(ADAPT_LO, ADAPT_HI, if (meanLuma.isNaN()) 0.1f else meanLuma)

    fun dropAdapt(meanLuma: Float): Float =
        1f + (ADAPT_BRIGHT_DROP - 1f) * smoothstep(ADAPT_LO, ADAPT_HI, if (meanLuma.isNaN()) 0.1f else meanLuma)

    /** Effective flash amount for the shader (0 = skipped). */
    fun effectiveStrength(look: RelightLook): Float = max(0f, look.strength) * strengthAdapt(look.ambientLuma)

    fun effectiveDrop(look: RelightLook): Float = clamp01(look.ambientDrop) * dropAdapt(look.ambientLuma)

    /** Light reaching a point at normalised distance [d] (distance / radius) from the subject: 1 at the subject, 0.25 at d = 1 (inverse-square-like: (1/(1+d^2))^2). */
    fun falloff(d: Float): Float { val q = 1f / (1f + d * d); return q * q }

    /** Natural coverage of a small flash: 1 over most of the frame, dropping toward the corners ([r] 0 centre .. 1 corner). */
    fun coverage(r: Float): Float = 1f - COVER_DROP * smoothstep(0.55f, 1.1f, r)

    /** Total light at output position ([u], [v]) of an image with [aspect] = w/h, 0..1. */
    fun light(look: RelightLook, u: Float, v: Float, aspect: Float): Float {
        val s = look.subject
        val dx = (u - s.cx) * aspect
        val dy = v - s.cy
        val rad = max(MIN_RADIUS, s.radius * look.reach)
        val d = sqrt(dx * dx + dy * dy) / rad
        return falloff(d) * coverage(FilmMath.radius(u, v, aspect))
    }

    /** Hue-preserving soft shoulder on the brightest channel. */
    fun shoulder(r: Float, g: Float, b: Float): FloatArray {
        val m = max(r, max(g, b))
        if (m <= SHOULDER_KNEE) return floatArrayOf(r, g, b)
        val span = 1f - SHOULDER_KNEE
        val m2 = SHOULDER_KNEE + span * (1f - exp(-(m - SHOULDER_KNEE) / span))
        val k = m2 / m
        val y = FilmMath.luma(r * k, g * k, b * k)
        val d = ROLL_DESAT * smoothstep(SHOULDER_KNEE, 1.5f, m)
        return floatArrayOf(
            r * k + (y - r * k) * d,
            g * k + (y - g * k) * d,
            b * k + (y - b * k) * d,
        )
    }

    /**
     * CPU reference of the shader's relight stage for one pixel. [r],[g],[b] are the sRGB-ish input, [illum] the LINEAR ambient
     * illumination at that pixel (for a flat colour it is the colour itself), [u],[v] the output position.
     * Returns sRGB-ish 0..1. Strength 0 returns the input unchanged.
     */
    fun apply(r: Float, g: Float, b: Float, illum: FloatArray, look: RelightLook, u: Float, v: Float, aspect: Float): Rgb {
        val strength = effectiveStrength(look)
        if (strength <= 0f) return Rgb(r, g, b)
        val drop = effectiveDrop(look)
        val lin = floatArrayOf(max(r, 0f).pow(FilmMath.GAMMA), max(g, 0f).pow(FilmMath.GAMMA), max(b, 0f).pow(FilmMath.GAMMA))
        val il = FilmMath.luma(illum[0], illum[1], illum[2])
        val tint = FilmMath.relightTint(look)
        val light = light(look, u, v, aspect)
        val o = FloatArray(3)
        for (c in 0..2) {
            val ill = max(il + (illum[c] - il) * CHROMA_KEEP, ILLUM_FLOOR)
            val alb = min(lin[c] / ill, ALBEDO_MAX)
            o[c] = lin[c] * (1f - drop * (1f - light)) + alb * tint[c] * (strength * light)
        }
        val s = shoulder(o[0], o[1], o[2])
        val inv = 1f / FilmMath.GAMMA
        return Rgb(clamp01(max(s[0], 0f).pow(inv)), clamp01(max(s[1], 0f).pow(inv)), clamp01(max(s[2], 0f).pow(inv)))
    }
}
