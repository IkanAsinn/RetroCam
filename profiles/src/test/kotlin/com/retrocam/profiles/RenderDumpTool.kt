package com.retrocam.profiles

import com.retrocam.filmengine.FilmMath
import com.retrocam.filmengine.FilmUniforms
import com.retrocam.filmengine.Lut
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a real test: a tool for the offline render harness (tools/render). It does nothing unless
 * RENDER_DUMP_DIR is set. For every requested profile x flash x size it runs
 * ProfileRepository -> ParamsMapper -> FilmParams -> FilmUniforms and writes JSON, plus the shader
 * sources. Environment:
 *  RENDER_DUMP_DIR  output directory (absolute)
 *  RENDER_PROFILES  comma list of profile ids, or "all" (default all)
 *  RENDER_SIZES     comma list of WxH (default 1080x1080)
 *  RENDER_FLASH     "0", "1" or "both" (default both)
 *  RENDER_GRAIN     OFF/LOW/NORMAL/HIGH (default NORMAL)
 *  RENDER_FLARE     OFF/AUTO/ALWAYS (default OFF)    RENDER_LEAKS OFF/SUBTLE/STRONG (default OFF)
 *  RENDER_STRENGTH  0..1 filter strength (default 1)  RENDER_SEED int (default 1234)
 */
class RenderDumpTool {
    private fun env(name: String, def: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: def

    @Test
    fun dump() {
        val dir = System.getenv("RENDER_DUMP_DIR")
        assumeTrue("RENDER_DUMP_DIR not set", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val repo = TestAssets.repository()
        val ids = env("RENDER_PROFILES", "all").let { s ->
            if (s == "all") repo.all(true).map { it.id } else s.split(",").map { it.trim() }
        }
        val sizes = env("RENDER_SIZES", "1080x1080").split(",").map { it.trim().split("x").let { p -> p[0].toInt() to p[1].toInt() } }
        val flashModes = when (env("RENDER_FLASH", "both")) { "0" -> listOf(false); "1" -> listOf(true); else -> listOf(false, true) }
        val toggles = EffectToggles(
            GrainLevel.valueOf(env("RENDER_GRAIN", "NORMAL")),
            FlareMode.valueOf(env("RENDER_FLARE", "OFF")),
            LeakMode.valueOf(env("RENDER_LEAKS", "OFF")),
        )
        val strength = env("RENDER_STRENGTH", "1").toFloat()
        val seed = env("RENDER_SEED", "1234").toInt()

        for ((name, src) in FilmUniforms.shaderSources()) File(out, name).writeText(src)
        for (id in ids) {
            val profile = repo.byId(id) ?: error("unknown profile $id")
            for (flash in flashModes) for ((w, h) in sizes) {
                val params = ParamsMapper.toFilmParams(profile, toggles, LensKind.MAIN, flash, seed, false).copy(strength = strength)
                val u = FilmUniforms.compute(params, w, h, 33)
                val json = JsonObject(
                    mapOf(
                        "profile" to JsonPrimitive(id),
                        "flash" to JsonPrimitive(flash),
                        "width" to JsonPrimitive(w),
                        "height" to JsonPrimitive(h),
                        "seed" to JsonPrimitive(params.seed),
                        "uniforms" to JsonObject(u.mapValues { (_, v) -> JsonArray(v.map { JsonPrimitive(it) }) }),
                    ),
                )
                File(out, "${id}_f${if (flash) 1 else 0}_${w}x$h.json").writeText(json.toString())
            }
        }
        // Copy the LUT strips next to the dumps so the renderer is self-contained.
        File(out, "luts").mkdirs()
        for (id in ids) File(TestAssets.lutsDir, "$id.png").copyTo(File(out, "luts/$id.png"), overwrite = true)
    }

    /**
     * Expected FilmMath.apply values on flat colours (grain/CA/blur/halation off), for check_filmmath.py, which
     * renders the same flat colours through the real shader. Needs the 256x192 dumps (RENDER_SIZES incl. 256x192).
     */
    @Test
    fun dumpFilmMathReference() {
        val dir = System.getenv("RENDER_DUMP_DIR")
        assumeTrue("RENDER_DUMP_DIR not set", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val repo = TestAssets.repository()
        val w = 256
        val h = 192
        val colours = listOf(
            intArrayOf(128, 128, 128), intArrayOf(200, 150, 100), intArrayOf(30, 40, 60), intArrayOf(240, 235, 225),
            intArrayOf(220, 40, 30), intArrayOf(20, 120, 40), intArrayOf(90, 90, 200), intArrayOf(10, 10, 10),
        )
        val pts = listOf(0.5f to 0.5f, 0.2f to 0.3f, 0.8f to 0.7f, 0.03f to 0.05f, 0.97f to 0.95f, 0.5f to 0.1f)
        val rows = ArrayList<kotlinx.serialization.json.JsonElement>()
        for (id in listOf("gold_snap", "instant_mini", "mono_snap", "slide_100", "expired_90s")) {
            val profile = repo.byId(id)!!
            val img = javax.imageio.ImageIO.read(File(TestAssets.lutsDir, "$id.png"))
            val argb = IntArray(img.width * img.height) { img.getRGB(it % img.width, it / img.width) }
            val lut = Lut.fromStripPixels(img.width, img.height, argb)
            for (flash in listOf(false, true)) {
                val p0 = ParamsMapper.toFilmParams(profile, EffectToggles(GrainLevel.OFF, FlareMode.OFF, LeakMode.OFF), LensKind.MAIN, flash, 1234, false)
                val p = p0.copy(chromaticAberrationPx = 0f, edgeSoftness = 0f, halationStrength = 0f)
                for (c in colours) for ((u, v) in pts) {
                    val px = (u * w).toInt().coerceIn(0, w - 1)
                    val py = (v * h).toInt().coerceIn(0, h - 1)
                    val uc = (px + 0.5f) / w
                    val vc = (py + 0.5f) / h
                    val e = FilmMath.apply(c[0] / 255f, c[1] / 255f, c[2] / 255f, p, lut, uc, vc, w.toFloat() / h)
                    rows += JsonObject(
                        mapOf(
                            "profile" to JsonPrimitive(id), "flash" to JsonPrimitive(flash),
                            "rgb" to JsonArray(c.map { JsonPrimitive(it) }),
                            "px" to JsonPrimitive(px), "py" to JsonPrimitive(py),
                            "expected" to JsonArray(listOf(e.r, e.g, e.b).map { JsonPrimitive(it) }),
                        ),
                    )
                }
            }
        }
        File(out, "filmmath_reference.json").writeText(JsonArray(rows).toString())
    }
}
