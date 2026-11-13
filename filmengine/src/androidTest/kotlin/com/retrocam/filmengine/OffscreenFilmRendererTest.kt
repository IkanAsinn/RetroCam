package com.retrocam.filmengine

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

/**
 * The PRD's "saved photo matches the preview" gate: preview and capture share the shader, so the
 * capture path (offscreen) must match the CPU reference [FilmMath] with every spatial effect off.
 */
@RunWith(AndroidJUnit4::class)
class OffscreenFilmRendererTest {
    private lateinit var renderer: OffscreenFilmRenderer

    @Before fun setUp() {
        renderer = OffscreenFilmRenderer()
    }

    @After fun tearDown() {
        renderer.release()
    }

    /** Horizontal grey ramp, vertical hue sweep in the top half and a grid of colour patches. */
    private fun syntheticImage(w: Int, h: Int): Bitmap {
        val px = IntArray(w * h)
        val patches = intArrayOf(
            Color.rgb(255, 0, 0), Color.rgb(0, 255, 0), Color.rgb(0, 0, 255), Color.rgb(255, 255, 0),
            Color.rgb(0, 255, 255), Color.rgb(255, 0, 255), Color.rgb(115, 82, 68), Color.rgb(194, 150, 130),
        )
        for (y in 0 until h) for (x in 0 until w) {
            val c = when {
                y < h / 3 -> {
                    val v = (255f * x / (w - 1)).toInt()
                    Color.rgb(v, v, v)
                }
                y < 2 * h / 3 -> {
                    val hsv = floatArrayOf(360f * x / w, 1f, 0.3f + 0.7f * (y - h / 3f) / (h / 3f))
                    Color.HSVToColor(hsv)
                }
                else -> patches[(x * patches.size / w).coerceIn(0, patches.size - 1)]
            }
            px[y * w + x] = c
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun goldenParams() = FilmParams(
        exposureEv = 0.4f,
        blackLift = 0.05f,
        contrast = 1.15f,
        saturation = 1.2f,
        vignetteStrength = 0.4f,
        vignetteSoftness = 0.6f,
        colorShift = Rgb(0.03f, -0.02f, 0.01f),
        // Everything below is off for the golden comparison.
        grainAmount = 0f, halationStrength = 0f, chromaticAberrationPx = 0f, edgeSoftness = 0f,
        flash = null, flare = null, leak = null,
    )

    /** A non-trivial LUT: cross-channel mixing so a wrong index order or half-texel error shows. */
    private fun testLut(): Lut {
        val size = 33
        val d = ByteArray(size * size * size * 3)
        var i = 0
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val fr = r / 32f; val fg = g / 32f; val fb = b / 32f
            val orr = (0.85f * fr + 0.10f * fg + 0.05f * fb).coerceIn(0f, 1f)
            val og = (0.05f * fr + 0.9f * fg + 0.05f * fb).coerceIn(0f, 1f)
            val ob = (0.2f * fr + 0.1f * fg + 0.7f * fb * fb + 0.05f).coerceIn(0f, 1f)
            d[i++] = (orr * 255f + 0.5f).toInt().toByte()
            d[i++] = (og * 255f + 0.5f).toInt().toByte()
            d[i++] = (ob * 255f + 0.5f).toInt().toByte()
        }
        return Lut(size, d)
    }

    private fun maxDelta(input: Bitmap, output: Bitmap, params: FilmParams, lut: Lut): Int {
        val w = input.width
        val h = input.height
        val aspect = w.toFloat() / h
        val inPx = IntArray(w * h)
        val outPx = IntArray(w * h)
        input.getPixels(inPx, 0, w, 0, 0, w, h)
        output.getPixels(outPx, 0, w, 0, 0, w, h)
        var worst = 0
        for (y in 0 until h step 3) for (x in 0 until w step 3) {
            val p = inPx[y * w + x]
            val e = FilmMath.apply(
                Color.red(p) / 255f, Color.green(p) / 255f, Color.blue(p) / 255f,
                params, lut, (x + 0.5f) / w, (y + 0.5f) / h, aspect,
            )
            val q = outPx[y * w + x]
            worst = max(worst, abs(Math.round(e.r * 255f) - Color.red(q)))
            worst = max(worst, abs(Math.round(e.g * 255f) - Color.green(q)))
            worst = max(worst, abs(Math.round(e.b * 255f) - Color.blue(q)))
        }
        return worst
    }

    @Test
    fun identityParamsReproduceInput() = runBlocking {
        val input = syntheticImage(640, 480)
        val out = renderer.render(input, FilmParams(), Lut.identity())
        assertEquals(640, out.width); assertEquals(480, out.height)
        assertTrue(maxDelta(input, out, FilmParams(), Lut.identity()) <= 3)
    }

    @Test
    fun goldenMatchesCpuReference() = runBlocking {
        val input = syntheticImage(800, 600)
        val lut = testLut()
        val params = goldenParams()
        val out = renderer.render(input, params, lut)
        val delta = maxDelta(input, out, params, lut)
        assertTrue("max channel delta $delta > 3/255", delta <= 3)
    }

    @Test
    fun tiledRenderIsSeamlessAndMatchesReference() = runBlocking {
        // 2300 x 1200: 3 x 2 tiles of 1024.
        val input = syntheticImage(2300, 1200)
        val lut = testLut()
        val params = goldenParams()
        val out = renderer.render(input, params, lut)
        assertEquals(2300, out.width); assertEquals(1200, out.height)
        val delta = maxDelta(input, out, params, lut)
        assertTrue("max channel delta $delta > 3/255", delta <= 3)
    }

    @Test
    fun twelveMegapixelInputKeepsDimensions() = runBlocking {
        val input = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(120, 140, 160))
        val out = renderer.render(input, FilmParams(grainAmount = 0.5f, vignetteStrength = 0.3f), Lut.identity())
        assertEquals(4000, out.width)
        assertEquals(3000, out.height)
        out.recycle()
    }

    @Test
    fun rendererIsReusable() = runBlocking {
        val a = renderer.render(syntheticImage(64, 48), FilmParams(), Lut.identity())
        val b = renderer.render(syntheticImage(100, 100), FilmParams(), testLut())
        assertEquals(64, a.width)
        assertEquals(100, b.width)
    }
}
