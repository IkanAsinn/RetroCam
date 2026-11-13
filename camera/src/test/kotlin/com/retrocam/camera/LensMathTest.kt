package com.retrocam.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensMathTest {
    @Test
    fun equivalentFocalLength() {
        // 9.6 x 7.2 mm sensor, 6.9 mm lens -> ~24.9 mm equivalent
        assertEquals(24.9f, LensMath.equivalentFocal(6.9f, 9.6f, 7.2f), 0.2f)
    }

    @Test
    fun ratiosAreRelativeToMain() {
        assertEquals(0.5f, LensMath.ratio(13f, 24f), 0f)
        assertEquals(3f, LensMath.ratio(72f, 24f), 0f)
        assertEquals(1f, LensMath.ratio(24f, 24f), 0f)
    }

    @Test
    fun usableRatios() {
        assertTrue(LensMath.isUsableRatio(0.6f))
        assertTrue(LensMath.isUsableRatio(3f))
        assertFalse(LensMath.isUsableRatio(1f))
        assertFalse(LensMath.isUsableRatio(1.2f)) // macro / depth / near-duplicate of the main lens
        assertFalse(LensMath.isUsableRatio(0.1f))
    }
}
