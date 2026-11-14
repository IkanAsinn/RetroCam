package com.retrocam.filmengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmMathTest {
    private val id = Lut.identity()

    @Test
    fun neutralParamsAreIdentity() {
        for (v in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            val o = FilmMath.apply(v, 1f - v, v / 2, FilmParams(), id, 0.5f, 0.5f, 1.33f)
            assertEquals(v, o.r, 1.5f / 255f)
            assertEquals(1f - v, o.g, 1.5f / 255f)
            assertEquals(v / 2, o.b, 1.5f / 255f)
        }
    }

    @Test
    fun exposureOneEvDoublesLinearLight() {
        val o = FilmMath.grade(0.25f, 0.25f, 0.25f, FilmParams(exposureEv = 1f), id)
        val expected = Math.pow(2.0 * Math.pow(0.25, 2.2), 1 / 2.2).toFloat()
        assertEquals(expected, o.r, 2f / 255f)
    }

    @Test
    fun contrastPivotsAroundMidGreyAndLiftRaisesBlacks() {
        val c = FilmMath.grade(0.5f, 0.5f, 0.5f, FilmParams(contrast = 1.4f), id)
        assertEquals(0.5f, c.r, 2f / 255f)
        val hi = FilmMath.grade(0.75f, 0.75f, 0.75f, FilmParams(contrast = 1.4f), id)
        assertEquals(0.85f, hi.r, 2f / 255f)
        val lifted = FilmMath.grade(0f, 0f, 0f, FilmParams(blackLift = 0.1f), id)
        assertEquals(0.1f, lifted.r, 2f / 255f)
    }

    @Test
    fun saturationZeroGivesGreyAndColourShiftIsAdded() {
        val g = FilmMath.grade(1f, 0f, 0f, FilmParams(saturation = 0f), id)
        assertEquals(g.r, g.g, 1e-4f)
        assertEquals(g.g, g.b, 1e-4f)
        assertEquals(0.2126f, g.r, 2f / 255f)
        val s = FilmMath.grade(0.5f, 0.5f, 0.5f, FilmParams(colorShift = Rgb(0.1f, 0f, -0.1f)), id)
        assertEquals(0.6f, s.r, 2f / 255f)
        assertEquals(0.4f, s.b, 2f / 255f)
    }

    @Test
    fun vignetteDarkensCornersNotCentre() {
        val p = FilmParams(vignetteStrength = 0.6f, vignetteSoftness = 0.5f)
        val centre = FilmMath.apply(1f, 1f, 1f, p, id, 0.5f, 0.5f, 1.5f)
        val corner = FilmMath.apply(1f, 1f, 1f, p, id, 0f, 0f, 1.5f)
        assertEquals(1f, centre.r, 1e-3f)
        assertTrue(corner.r < 0.5f)
        assertEquals(1f, FilmMath.radius(0f, 0f, 1.5f), 1e-4f)
    }

    @Test
    fun kelvinTintIsNeutralAt6500AndWarmBelow() {
        val white = FilmMath.flashTint(FlashLook(0.5f, 0.5f, 6500f, 1f))
        assertEquals(1f, white[0], 1e-4f)
        assertEquals(1f, white[2], 1e-4f)
        val warm = FilmMath.flashTint(FlashLook(0.5f, 0.5f, 3200f, 1f))
        assertTrue(warm[2] < warm[0])
    }

    @Test
    fun flashHasNoSyntheticHotspotOnlySubtleTint() {
        val flash = FlashLook(hotspotSize = 1f, falloff = 0f, kelvin = 3200f, strength = 1f, backgroundDrop = 0f)
        val on = FilmParams(flash = flash)
        // A bright pixel: centre and corner get the same (uniform) tint, no brightening hotspot at the centre.
        val centre = FilmMath.apply(0.7f, 0.7f, 0.7f, on, id, 0.5f, 0.5f, 1.33f)
        val corner = FilmMath.apply(0.7f, 0.7f, 0.7f, on, id, 0.02f, 0.02f, 1.33f)
        assertEquals(centre.r, corner.r, 1.5f / 255f)
        assertEquals(centre.b, corner.b, 1.5f / 255f)
        val off = FilmMath.apply(0.7f, 0.7f, 0.7f, FilmParams(), id, 0.5f, 0.5f, 1.33f)
        assertTrue(centre.r >= off.r - 1e-3f && centre.r - off.r < 0.08f)
        assertTrue(centre.b < off.b && off.b - centre.b < 0.2f)   // warm tint, subtle
        // Neutral flash light (6500 K) leaves colours untouched.
        val white = FilmParams(flash = FlashLook(0.5f, 0.5f, 6500f, 1f, backgroundDrop = 0f))
        assertEquals(0.7f, FilmMath.apply(0.7f, 0.7f, 0.7f, white, id, 0.5f, 0.5f, 1.33f).b, 1.5f / 255f)
    }

    @Test
    fun uniformsMirrorParams() {
        val p = FilmParams(exposureEv = 1f, grainAmount = 0.4f, grainSizePx = 1.5f, vignetteStrength = 0.3f)
        val u = FilmUniforms.compute(p, 4000, 3000, 33)
        assertEquals(2f, u.getValue("uExposureMul")[0], 1e-6f)
        assertEquals(3000f / 1080f, u.getValue("uScale")[0], 1e-6f)
        assertEquals(listOf(4000f, 3000f), u.getValue("uRes").toList())
        assertEquals(listOf(0.4f, 1.5f), u.getValue("uGrain").toList())
        assertEquals(0f, u.getValue("uFlash")[2], 0f)
        assertEquals(0f, u.getValue("uFlashBg")[0], 0f)
    }

    @Test
    fun shaderSourcesContainBothPathVariants() {
        val preview = FilmShader.fragmentSource(external = true)
        val capture = FilmShader.fragmentSource(external = false)
        assertTrue(preview.startsWith("#version 300 es"))
        assertTrue(preview.contains("GL_OES_EGL_image_external_essl3"))
        assertTrue(preview.contains("#define SCENE_SAMPLER samplerExternalOES"))
        assertTrue(capture.contains("#define SCENE_SAMPLER sampler2D"))
        // Same body after the prefix.
        assertEquals(preview.substringAfter("void main()"), capture.substringAfter("void main()"))
    }

    private val strong = FilmParams(contrast = 1.4f, saturation = 0.3f, colorShift = Rgb(0.1f, 0f, -0.1f), vignetteStrength = 0.5f)

    private val bgFlash = FlashLook(0.5f, 0.5f, 6500f, 0f, backgroundDrop = 0.6f)

    @Test
    fun flashBackgroundDropsDarkPixelsFarFromCentre() {
        val p = FilmParams(flash = bgFlash)
        val o = FilmMath.apply(0.15f, 0.15f, 0.15f, p, id, 0f, 0f, 1.5f)
        assertTrue("dark corner pixel should fall a lot, was ${o.r}", o.r < 0.15f * 0.5f)
    }

    @Test
    fun flashBackgroundKeepsBrightCentre() {
        val p = FilmParams(flash = bgFlash)
        val o = FilmMath.apply(0.6f, 0.6f, 0.6f, p, id, 0.5f, 0.5f, 1.5f)
        assertEquals(0.6f, o.r, 1.5f / 255f)
        // a lit face (luma 0.5) even at the edge is untouched
        val e = FilmMath.apply(0.5f, 0.5f, 0.5f, p, id, 0f, 0f, 1.5f)
        assertEquals(0.5f, e.r, 1.5f / 255f)
    }

    @Test
    fun flashBackgroundDropIsSmoothAndMonotonic() {
        var prev = -1f
        for (i in 0..100) {
            val v = i / 100f
            val k = FilmMath.backgroundKeep(bgFlash, v, 1f)
            assertTrue(k >= prev - 1e-6f)
            prev = k
        }
    }

    @Test
    fun noFlashOrZeroDropMeansNoBackgroundChange() {
        val a = FilmMath.apply(0.1f, 0.1f, 0.1f, FilmParams(), id, 0f, 0f, 1.5f)
        assertEquals(0.1f, a.r, 1.5f / 255f)
        val b = FilmMath.apply(0.1f, 0.1f, 0.1f, FilmParams(flash = bgFlash.copy(backgroundDrop = 0f)), id, 0f, 0f, 1.5f)
        assertEquals(0.1f, b.r, 1.5f / 255f)
    }

    @Test
    fun strengthZeroIgnoresFlashBackgroundDrop() {
        val o = FilmMath.apply(0.1f, 0.1f, 0.1f, FilmParams(flash = bgFlash, strength = 0f), id, 0f, 0f, 1.5f)
        assertEquals(0.1f, o.r, 1e-6f)
    }

    @Test
    fun strengthZeroReturnsTheInputUnchanged() {
        val o = FilmMath.apply(0.3f, 0.6f, 0.9f, strong.copy(strength = 0f), id, 0f, 0f, 1.5f)
        assertEquals(0.3f, o.r, 1e-6f)
        assertEquals(0.6f, o.g, 1e-6f)
        assertEquals(0.9f, o.b, 1e-6f)
    }

    @Test
    fun strengthOneIsTheFullLookAndDefault() {
        val full = FilmMath.apply(0.3f, 0.6f, 0.9f, strong, id, 0.1f, 0.2f, 1.5f)
        val one = FilmMath.apply(0.3f, 0.6f, 0.9f, strong.copy(strength = 1f), id, 0.1f, 0.2f, 1.5f)
        assertEquals(full, one)
        assertEquals(1f, FilmParams().strength, 0f)
        assertTrue("look must differ from input", kotlin.math.abs(full.r - 0.3f) > 0.02f)
    }

    @Test
    fun strengthHalfIsTheMidpoint() {
        val full = FilmMath.apply(0.3f, 0.6f, 0.9f, strong, id, 0.1f, 0.2f, 1.5f)
        val half = FilmMath.apply(0.3f, 0.6f, 0.9f, strong.copy(strength = 0.5f), id, 0.1f, 0.2f, 1.5f)
        assertEquals((0.3f + full.r) / 2, half.r, 1e-5f)
        assertEquals((0.6f + full.g) / 2, half.g, 1e-5f)
        assertEquals((0.9f + full.b) / 2, half.b, 1e-5f)
    }

    @Test
    fun strengthIsClamped() {
        assertEquals(0f, FilmMath.clampStrength(-3f), 0f)
        assertEquals(1f, FilmMath.clampStrength(7f), 0f)
        assertEquals(1f, FilmMath.clampStrength(Float.NaN), 0f)
        assertEquals(0.25f, FilmMath.clampStrength(0.25f), 0f)
    }
}
