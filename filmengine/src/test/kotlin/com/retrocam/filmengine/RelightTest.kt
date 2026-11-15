package com.retrocam.filmengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelightTest {
    private val look = RelightLook(kelvin = 5600f, strength = 0.7f, ambientDrop = 0.5f, ambientLuma = 0.03f)

    private fun lin(v: Float) = Math.pow(v.toDouble(), 2.2).toFloat()
    private fun flatIllum(v: Float) = floatArrayOf(lin(v), lin(v), lin(v))

    private fun flat(v: Float, l: RelightLook, u: Float = 0.5f, vv: Float = 0.45f): Float =
        RelightMath.apply(v, v, v, flatIllum(v), l, u, vv, 1.33f).g

    @Test
    fun strengthZeroIsIdentity() {
        val off = look.copy(strength = 0f)
        for (v in listOf(0f, 0.1f, 0.5f, 0.9f)) {
            val o = RelightMath.apply(v, v * 0.5f, v * 0.2f, flatIllum(v), off, 0.3f, 0.3f, 1.33f)
            assertEquals(v, o.r, 0f)
            assertEquals(v * 0.5f, o.g, 0f)
            assertEquals(v * 0.2f, o.b, 0f)
        }
        // null relight in the full pipeline reference is the old path
        val a = FilmMath.apply(0.4f, 0.3f, 0.2f, FilmParams(), Lut.identity(), 0.5f, 0.5f, 1.33f)
        val b = FilmMath.apply(0.4f, 0.3f, 0.2f, FilmParams(relight = off), Lut.identity(), 0.5f, 0.5f, 1.33f)
        assertEquals(a, b)
    }

    @Test
    fun uniformsAreZeroWhenOffAndFilledWhenOn() {
        val off = FilmUniforms.compute(FilmParams(), 100, 100, 33)
        assertEquals(0f, off.getValue("uRelight")[0], 0f)
        val on = FilmUniforms.compute(FilmParams(relight = look, flash = FlashLook(0.5f, 0.5f, 5600f, 0.7f)), 100, 100, 33)
        assertTrue(on.getValue("uRelight")[0] > 0f)
        assertEquals(look.subject.cx, on.getValue("uRelightSubj")[0], 0f)
        assertEquals(look.subject.radius * look.reach, on.getValue("uRelightSubj")[2], 1e-6f)
        assertEquals(3, on.getValue("uRelightTint").size)
        assertEquals(0f, FilmUniforms.compute(FilmParams(relight = look.copy(strength = 0f)), 10, 10, 33).getValue("uRelight")[0], 0f)
    }

    @Test
    fun dimSceneGetsStrongerLiftThanBrightScene() {
        val dim = look.copy(ambientLuma = 0.02f)
        val bright = look.copy(ambientLuma = 0.4f)
        val v = 0.35f
        val liftDim = flat(v, dim) - v
        val liftBright = flat(v, bright) - v
        assertTrue("dim $liftDim vs bright $liftBright", liftDim > liftBright)
        assertTrue(RelightMath.effectiveStrength(dim) > RelightMath.effectiveStrength(bright))
        assertTrue(RelightMath.effectiveDrop(dim) > RelightMath.effectiveDrop(bright))
    }

    @Test
    fun falloffIsMonotonicAndHasNoHotspotEdge() {
        var prev = RelightMath.falloff(0f)
        assertEquals(1f, prev, 1e-6f)
        var d = 0.05f
        while (d < 3f) {
            val f = RelightMath.falloff(d)
            assertTrue(f < prev)
            assertTrue("steps stay small (no visible circle edge)", prev - f < 0.12f)
            prev = f
            d += 0.05f
        }
        // light along a ray from the subject never increases
        var last = 2f
        for (i in 0..40) {
            val l = RelightMath.light(look, 0.5f + i * 0.0125f, 0.44f, 1.33f)
            assertTrue(l <= last + 1e-6f)
            last = l
        }
        // the lit area covers most of the frame: the frame corner still gets a real share of the light
        assertTrue(RelightMath.light(look, 0.02f, 0.02f, 1.33f) > 0.15f)
        assertTrue(RelightMath.light(look, 0.5f, 0.44f, 1.33f) > 0.99f)
    }

    @Test
    fun subjectIsBrighterThanTheBackground() {
        val centre = flat(0.3f, look, 0.5f, 0.44f)
        val corner = flat(0.3f, look, 0.03f, 0.97f)
        assertTrue(centre > corner)
    }

    @Test
    fun noNanOrInfinityWithZeroIllumination() {
        for (v in listOf(0f, 0.001f, 0.5f, 1f)) {
            val o = RelightMath.apply(v, v, v, floatArrayOf(0f, 0f, 0f), look, 0.5f, 0.5f, 1.33f)
            for (c in listOf(o.r, o.g, o.b)) {
                assertFalse(c.isNaN() || c.isInfinite())
                assertTrue(c in 0f..1f)
            }
        }
        assertFalse(RelightMath.strengthAdapt(Float.NaN).isNaN())
        val o = RelightMath.apply(0.2f, 0.2f, 0.2f, flatIllum(0.2f), look.copy(subject = FlashSubject(0.5f, 0.5f, 0f, 0)), 0.5f, 0.5f, 1f)
        assertFalse(o.r.isNaN())
    }

    @Test
    fun highlightsRollOffInsteadOfClipping() {
        val bright = look.copy(strength = 1.2f)
        val a = flat(0.8f, bright)
        val b = flat(0.95f, bright)
        assertTrue(b >= a)       // still ordered
        assertTrue(b <= 1f)
        val s = RelightMath.shoulder(2f, 1.5f, 1f)
        assertTrue(s.all { it < 1.001f })
        assertEquals(0.3f, RelightMath.shoulder(0.3f, 0.2f, 0.1f)[0], 0f) // below the knee untouched
    }

    @Test
    fun flashPullsAmbientCastTowardNeutral() {
        // warm ambient: a grey wall under tungsten light reads orange; the flash term follows reflectance, so it cools
        val warm = floatArrayOf(lin(0.5f), lin(0.35f), lin(0.18f))
        val before = floatArrayOf(0.5f, 0.35f, 0.18f)
        val o = RelightMath.apply(before[0], before[1], before[2], warm, look, 0.5f, 0.44f, 1.33f)
        assertTrue("blue/red ratio rises", o.b / o.r > before[2] / before[0])
    }

    @Test
    fun filmMathAppliesRelightAndSkipsBackgroundDropWhenOn() {
        val flash = FlashLook(0.5f, 0.5f, 5600f, 0.7f, backgroundDrop = 0.65f)
        val plain = FilmMath.apply(0.3f, 0.3f, 0.3f, FilmParams(flash = flash), Lut.identity(), 0.5f, 0.44f, 1.33f)
        val film = FilmMath.apply(0.3f, 0.3f, 0.3f, FilmParams(flash = flash, relight = look), Lut.identity(), 0.5f, 0.44f, 1.33f)
        assertNotEquals(plain, film)
        assertTrue(film.g > plain.g)
    }

    // ---- subject ----
    @Test
    fun noFaceGivesTheWideDefaultSlightlyAboveCentre() {
        val s = FlashSubject.fromFaces(emptyList())
        assertEquals(FlashSubject.DEFAULT, s)
        assertTrue(s.cy < 0.5f && s.radius >= 0.8f && s.faceCount == 0)
        assertEquals(FlashSubject.DEFAULT, FlashSubject.fromFaces(listOf(FaceBox(2f, 0.5f, 0.2f), FaceBox(0.5f, 0.5f, 0f))))
    }

    @Test
    fun faceMovesTheLightCentreButNeverAHotspotRadius() {
        val s = FlashSubject.fromFaces(listOf(FaceBox(0.2f, 0.25f, 0.1f)))
        assertEquals(1, s.faceCount)
        assertTrue(s.cx < 0.5f && s.cx > 0.2f) // pulled toward the face, not onto it
        assertTrue(s.cy < 0.5f && s.cy > 0.25f)
        assertEquals(0.8f, s.radius, 0f)        // small face: still a wide light
        assertEquals(1.25f, FlashSubject.fromFaces(listOf(FaceBox(0.5f, 0.5f, 0.9f))).radius, 0f)
    }

    @Test
    fun biggerFaceWinsTheCentroid() {
        val s = FlashSubject.fromFaces(listOf(FaceBox(0.2f, 0.5f, 0.1f), FaceBox(0.8f, 0.5f, 0.4f)))
        assertEquals(2, s.faceCount)
        assertTrue(s.cx > 0.5f)
    }

    // ---- illumination map ----
    @Test
    fun illumMapOfAFlatImageIsFlatAndKeepsAspect() {
        val w = 400
        val h = 300
        val px = IntArray(w * h) { 0xFF000000.toInt() or (128 shl 16) or (100 shl 8) or 60 }
        val m = IllumMapper.fromArgb(px, w, h)
        assertEquals(96, m.width)
        assertEquals(72, m.height)
        val expect = Math.pow(128 / 255.0, 2.2).toFloat()
        for (i in 0 until m.width * m.height) assertEquals(expect, m.linear[i * 3], 1e-3f)
        assertEquals(m.width * m.height * 3, m.toGammaBytes().size)
        assertEquals(128, m.toGammaBytes()[0].toInt() and 0xFF)
        assertTrue(m.meanLuma > 0f)
    }

    @Test
    fun illumMapIsEdgeAwareButSmooth() {
        val w = 96
        val h = 96
        // left half dark, right half bright: the blur must not smear the step across the edge
        val px = IntArray(w * h) { if (it % w < w / 2) 0xFF202020.toInt() else 0xFFE0E0E0.toInt() }
        val m = IllumMapper.fromArgb(px, w, h)
        val dark = m.linear[(48 * w + 40) * 3]
        val bright = m.linear[(48 * w + 56) * 3]
        assertTrue(bright > dark * 10f)
    }

    @Test
    fun smallImagesAreNotUpscaled() {
        val m = IllumMapper.fromArgb(IntArray(10 * 8) { 0xFF808080.toInt() }, 10, 8)
        assertEquals(10, m.width)
        assertEquals(8, m.height)
    }
}
