package com.retrocam.filmengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilmParamsTest {
    @Test
    fun defaultsAreNeutral() {
        val p = FilmParams()
        assertEquals(0f, p.exposureEv, 0f)
        assertEquals(1f, p.contrast, 0f)
        assertNull(p.flash)
        assertEquals(1080, FilmParams.REFERENCE_SHORT_EDGE)
    }
}
