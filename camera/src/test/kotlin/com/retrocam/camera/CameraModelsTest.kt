package com.retrocam.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraModelsTest {
    @Test
    fun zoomStopHoldsValues() {
        val stop = ZoomStop(0.6f, "0.6×")
        assertEquals(0.6f, stop.ratio, 0f)
        assertEquals("0.6×", stop.label)
    }
}
