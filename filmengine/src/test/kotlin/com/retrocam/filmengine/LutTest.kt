package com.retrocam.filmengine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LutTest {
    private fun assertRgb(expected: Triple<Float, Float, Float>, actual: Rgb, tol: Float = 1e-4f) {
        assertEquals(expected.first, actual.r, tol)
        assertEquals(expected.second, actual.g, tol)
        assertEquals(expected.third, actual.b, tol)
    }

    @Test
    fun identityHasDocumentedLayoutAndIndexOrder() {
        val lut = Lut.identity()
        assertEquals(33, lut.size)
        assertEquals(33 * 33 * 33 * 3, lut.rgb.size)
        // r varies fastest, then g, then b.
        assertEquals(0, lut.index(0, 0, 0))
        assertEquals(3, lut.index(1, 0, 0))
        assertEquals(33 * 3, lut.index(0, 1, 0))
        assertEquals(33 * 33 * 3, lut.index(0, 0, 1))
        val i = lut.index(32, 16, 8)
        assertEquals(255, lut.rgb[i].toInt() and 0xFF)
        assertEquals(128, lut.rgb[i + 1].toInt() and 0xFF)
        assertEquals(64, lut.rgb[i + 2].toInt() and 0xFF)
    }

    @Test
    fun rejectsWrongDataSize() {
        assertThrows(IllegalArgumentException::class.java) { Lut(3, ByteArray(10)) }
    }

    @Test
    fun identitySamplingIsExactAtLatticePoints() {
        val lut = Lut.identity()
        for (k in 0..32 step 4) {
            val v = k / 32f
            val s = lut.sample(v, v, v)
            // 8-bit quantisation only: within half a code value.
            assertRgb(Triple(v, v, v), s, 0.5f / 255f + 1e-5f)
        }
        // Corners are exact.
        assertRgb(Triple(0f, 0f, 0f), lut.sample(0f, 0f, 0f))
        assertRgb(Triple(1f, 1f, 1f), lut.sample(1f, 1f, 1f))
        assertRgb(Triple(1f, 0f, 1f), lut.sample(1f, 0f, 1f))
    }

    @Test
    fun trilinearInterpolatesBetweenLatticePoints() {
        // 2x2x2 LUT: value = 255 * (r + g + b) / 3-ish, designed so each corner is distinct.
        val data = ByteArray(2 * 2 * 2 * 3)
        fun set(r: Int, g: Int, b: Int, vr: Int, vg: Int, vb: Int) {
            val i = ((b * 2 + g) * 2 + r) * 3
            data[i] = vr.toByte(); data[i + 1] = vg.toByte(); data[i + 2] = vb.toByte()
        }
        for (b in 0..1) for (g in 0..1) for (r in 0..1) set(r, g, b, r * 200, g * 100, b * 50)
        val lut = Lut(2, data)
        assertRgb(Triple(100f / 255f, 50f / 255f, 25f / 255f), lut.sample(0.5f, 0.5f, 0.5f))
        assertRgb(Triple(50f / 255f, 0f, 0f), lut.sample(0.25f, 0f, 0f))
        assertRgb(Triple(200f / 255f, 25f / 255f, 50f / 255f), lut.sample(1f, 0.25f, 1f))
        // Out-of-range inputs clamp.
        assertRgb(Triple(200f / 255f, 100f / 255f, 50f / 255f), lut.sample(5f, 2f, 9f))
    }

    @Test
    fun interpolatesAcrossRealLatticeCells() {
        val lut = Lut.identity()
        // Identity is linear, so interpolation at arbitrary points must be close to the input.
        val s = lut.sample(0.3f, 0.61f, 0.87f)
        assertRgb(Triple(0.3f, 0.61f, 0.87f), s, 1.0f / 255f)
    }

    @Test
    fun stripRoundTrip() {
        val data = ByteArray(5 * 5 * 5 * 3) { (it * 7 + 3).toByte() }
        val lut = Lut(5, data)
        val strip = lut.toStripPixels()
        val back = Lut.fromStripPixels(25, 5, strip)
        assertEquals(5, back.size)
        assertArrayEquals(data, back.rgb)
    }

    @Test
    fun stripPixelPositionFollowsDocumentedLayout() {
        val size = 33
        val argb = IntArray(size * size * size)
        // pixel (x = b*33 + r, y = g)
        val r = 5; val g = 7; val b = 30
        argb[g * 1089 + b * 33 + r] = (0xFF shl 24) or (10 shl 16) or (20 shl 8) or 30
        val lut = Lut.fromStripPixels(1089, 33, argb)
        val i = lut.index(r, g, b)
        assertEquals(10, lut.rgb[i].toInt() and 0xFF)
        assertEquals(20, lut.rgb[i + 1].toInt() and 0xFF)
        assertEquals(30, lut.rgb[i + 2].toInt() and 0xFF)
    }

    @Test
    fun identityStripRoundTrip() {
        val id = Lut.identity()
        val back = Lut.fromStripPixels(1089, 33, id.toStripPixels())
        assertArrayEquals(id.rgb, back.rgb)
    }

    @Test
    fun badStripShapeRejected() {
        assertThrows(IllegalArgumentException::class.java) { Lut.fromStripPixels(100, 33, IntArray(3300)) }
    }
}
