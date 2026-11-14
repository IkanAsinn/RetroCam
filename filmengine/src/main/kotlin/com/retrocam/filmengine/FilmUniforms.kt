package com.retrocam.filmengine

import kotlin.math.min
import kotlin.math.pow

/**
 * The single place where [FilmParams] become shader uniform VALUES. Pure Kotlin (no GL), so
 * [FilmProgram.setUniforms] uses it for the GL calls and the offline render harness
 * (`tools/render`) dumps exactly the same numbers from a JVM test.
 *
 * Not included (set by the caller, depend on the draw rather than the look): `uQuadRect`,
 * `uTopDown`, `uTexMat`, `uScene`, `uLut`, and the int uniform `uSeed`.
 */
object FilmUniforms {
    /** Uniform name -> 1..4 floats, in insertion order. */
    fun compute(p: FilmParams, outW: Int, outH: Int, lutSize: Int): Map<String, FloatArray> {
        val m = LinkedHashMap<String, FloatArray>()
        m["uRes"] = floatArrayOf(outW.toFloat(), outH.toFloat())
        m["uScale"] = floatArrayOf(min(outW, outH).toFloat() / FilmParams.REFERENCE_SHORT_EDGE)
        m["uExposureMul"] = floatArrayOf(2f.pow(p.exposureEv))
        m["uBlackLift"] = floatArrayOf(p.blackLift)
        m["uContrast"] = floatArrayOf(p.contrast)
        m["uSaturation"] = floatArrayOf(p.saturation)
        m["uColorShift"] = floatArrayOf(p.colorShift.r, p.colorShift.g, p.colorShift.b)
        m["uLutSize"] = floatArrayOf(lutSize.toFloat())
        m["uCaPx"] = floatArrayOf(p.chromaticAberrationPx)
        m["uEdgeSoft"] = floatArrayOf(p.edgeSoftness)

        m["uFlashBg"] = floatArrayOf(p.flash?.backgroundDrop?.coerceIn(0f, 1f) ?: 0f)
        val f = p.flash
        if (f != null && f.strength > 0f) {
            m["uFlash"] = floatArrayOf(f.hotspotSize, f.falloff, f.strength)
            m["uFlashTint"] = FilmMath.flashTint(f)
        } else {
            m["uFlash"] = floatArrayOf(0f, 0f, 0f)
            m["uFlashTint"] = floatArrayOf(1f, 1f, 1f)
        }

        m["uHalStrength"] = floatArrayOf(p.halationStrength)
        m["uHalTint"] = floatArrayOf(p.halationTint.r, p.halationTint.g, p.halationTint.b)

        val fl = p.flare
        m["uFlare"] = if (fl != null && fl.strength > 0f) {
            floatArrayOf(if (fl.style == FlareStyle.WARM) 1f else 2f, fl.strength, fl.centerX, fl.centerY)
        } else {
            floatArrayOf(0f, 0f, 0.5f, 0.5f)
        }

        val lk = p.leak
        if (lk != null && lk.strength > 0f) {
            m["uLeak"] = floatArrayOf(lk.strength, lk.angle, lk.offset, 0f)
            m["uLeakTint"] = floatArrayOf(lk.tint.r, lk.tint.g, lk.tint.b)
        } else {
            m["uLeak"] = floatArrayOf(0f, 0f, 0f, 0f)
            m["uLeakTint"] = floatArrayOf(0f, 0f, 0f)
        }

        m["uVig"] = floatArrayOf(p.vignetteStrength, p.vignetteSoftness)
        m["uGrain"] = floatArrayOf(p.grainAmount, p.grainSizePx)
        m["uStrength"] = floatArrayOf(FilmMath.clampStrength(p.strength))
        return m
    }

    /** Shader sources by file name (for the offline render harness / glslangValidator). */
    fun shaderSources(): Map<String, String> = linkedMapOf(
        "film.vert" to FilmShader.VERTEX,
        "film_2d.frag" to FilmShader.fragmentSource(false),
        "film_ext.frag" to FilmShader.fragmentSource(true),
        "pass_2d.frag" to FilmShader.passthroughSource(false),
        "pass_ext.frag" to FilmShader.passthroughSource(true),
    )
}
