package com.retrocam

import com.retrocam.camera.ExposureInfo
import com.retrocam.camera.FlashMode
import com.retrocam.logic.ExposureLogic
import com.retrocam.logic.Overlay
import com.retrocam.logic.Overlays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureLogicTest {
    private val thirds = ExposureInfo(true, -12, 12, 1f / 3f)
    private val halves = ExposureInfo(true, -4, 4, 0.5f)

    @Test fun formatsZeroPlusAndRealMinus() {
        assertEquals("±0", ExposureLogic.format(0f))
        assertEquals("±0", ExposureLogic.format(-0.04f))
        assertEquals("+0.7", ExposureLogic.format(thirds, 2))
        assertEquals("−1.3", ExposureLogic.format(thirds, -4))
        assertEquals("+2.0", ExposureLogic.format(halves, 4))
        assertEquals("+4.0", ExposureLogic.format(thirds, 12))
    }

    @Test fun indexEvConversionWithRationalStep() {
        assertEquals(1f, thirds.evFor(3), 1e-5f)
        assertEquals(-2, halves.indexFor(-1f))
        assertEquals(2, thirds.indexFor(0.7f))
        assertEquals(0, thirds.indexFor(0f))
    }

    @Test fun clampsToNewCameraRange() {
        assertEquals(4, ExposureLogic.indexAfterRebind(2f, halves))
        assertEquals(4, ExposureLogic.indexAfterRebind(5f, halves))
        assertEquals(-4, ExposureLogic.indexAfterRebind(-9f, halves))
        assertEquals(0, ExposureLogic.indexAfterRebind(1f, ExposureInfo.UNSUPPORTED))
        // 1.0 EV is index 3 on a 1/3-step camera and index 2 on a 1/2-step camera
        assertEquals(3, ExposureLogic.indexAfterRebind(1f, thirds))
        assertEquals(2, ExposureLogic.indexAfterRebind(1f, halves))
    }

    @Test fun sliderSnapsAndCountsSteps() {
        assertEquals(3, ExposureLogic.snapIndex(2.6f, thirds))
        assertEquals(12, ExposureLogic.snapIndex(40f, thirds))
        assertEquals(-4, ExposureLogic.snapIndex(-9f, halves))
        assertEquals(23, ExposureLogic.sliderSteps(thirds))
        assertEquals(0, ExposureLogic.sliderSteps(ExposureInfo.UNSUPPORTED))
        assertEquals(0.5f, ExposureLogic.evForIndex(halves, 1), 1e-5f)
        assertEquals(2f, ExposureLogic.evForIndex(halves, 99), 1e-5f)
    }

    @Test fun onlyOneOverlayAtATime() {
        assertEquals(Overlay.EXPOSURE, Overlays.toggle(Overlay.STRENGTH, Overlay.EXPOSURE))
        assertEquals(Overlay.STRENGTH, Overlays.toggle(Overlay.EXPOSURE, Overlay.STRENGTH))
        assertEquals(Overlay.NONE, Overlays.toggle(Overlay.EXPOSURE, Overlay.EXPOSURE))
        assertEquals(Overlay.EXPOSURE, Overlays.toggle(Overlay.NONE, Overlay.EXPOSURE))
    }

    @Test fun exposureOverlayNeedsSupport() {
        assertEquals(Overlay.EXPOSURE, Overlays.visible(Overlay.EXPOSURE, true, true))
        assertEquals(Overlay.NONE, Overlays.visible(Overlay.EXPOSURE, true, false))
        assertEquals(Overlay.NONE, Overlays.visible(Overlay.STRENGTH, false, true))
        assertEquals(Overlay.STRENGTH, Overlays.visible(Overlay.STRENGTH, true, false))
    }

    @Test fun aeLockOnlyWithoutFlash() {
        assertTrue(ExposureLogic.aeLockAllowed(FlashMode.OFF, true))
        assertFalse(ExposureLogic.aeLockAllowed(FlashMode.AUTO, true))
        assertFalse(ExposureLogic.aeLockAllowed(FlashMode.ON, true))
        assertTrue(ExposureLogic.aeLockAllowed(FlashMode.ON, false))
    }
}
