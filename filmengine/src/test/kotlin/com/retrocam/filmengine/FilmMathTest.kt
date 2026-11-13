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
}
