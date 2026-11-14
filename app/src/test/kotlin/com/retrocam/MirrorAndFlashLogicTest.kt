package com.retrocam

import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.logic.ExposureLogic
import com.retrocam.logic.FlashLogic
import com.retrocam.logic.MirrorLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorAndFlashLogicTest {
    @Test
    fun mirrorOnlyWhenSettingOnAndFrontCamera() {
        assertTrue(MirrorLogic.shouldMirror(true, Facing.FRONT))
        assertFalse(MirrorLogic.shouldMirror(true, Facing.BACK))
        assertFalse(MirrorLogic.shouldMirror(false, Facing.FRONT))
        assertFalse(MirrorLogic.shouldMirror(false, Facing.BACK))
    }

    @Test
    fun frontFlashMapsToScreenFlash() {
        assertEquals(FlashMode.ON, FlashLogic.effective(FlashMode.ON, Facing.FRONT, false))
        assertEquals(FlashMode.ON, FlashLogic.effective(FlashMode.AUTO, Facing.FRONT, false)) // AUTO does not exist on front
        assertEquals(FlashMode.OFF, FlashLogic.effective(FlashMode.OFF, Facing.FRONT, false))
        assertTrue(FlashLogic.usesScreenFlash(FlashMode.ON, Facing.FRONT))
        assertFalse(FlashLogic.usesScreenFlash(FlashMode.OFF, Facing.FRONT))
        assertFalse(FlashLogic.usesScreenFlash(FlashMode.ON, Facing.BACK))
    }

    @Test
    fun backFlashIsUnchanged() {
        for (m in FlashMode.entries) {
            assertEquals(m, FlashLogic.effective(m, Facing.BACK, true))
            assertEquals(FlashMode.OFF, FlashLogic.effective(m, Facing.BACK, false))
        }
        assertTrue(FlashLogic.available(Facing.BACK, true))
        assertFalse(FlashLogic.available(Facing.BACK, false))
        assertTrue(FlashLogic.available(Facing.FRONT, false))
    }

    @Test
    fun cycleOnFrontIsOffOnOnly() {
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.OFF, Facing.FRONT))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.ON, Facing.FRONT))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.AUTO, Facing.FRONT)) // stored AUTO reads as ON
        assertEquals(FlashMode.ON, FlashLogic.displayMode(FlashMode.AUTO, Facing.FRONT))
        assertEquals(FlashMode.AUTO, FlashLogic.next(FlashMode.OFF, Facing.BACK))
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.AUTO, Facing.BACK))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.ON, Facing.BACK))
    }

    @Test
    fun aeLockIsBlockedByTheFrontScreenFlash() {
        fun allowed(flash: FlashMode, facing: Facing, unit: Boolean) =
            ExposureLogic.aeLockAllowed(flash, FlashLogic.available(facing, unit))
        assertFalse(allowed(FlashMode.ON, Facing.FRONT, false))
        assertTrue(allowed(FlashMode.OFF, Facing.FRONT, false))
        assertTrue(allowed(FlashMode.ON, Facing.BACK, false)) // back without a unit: flash is moot
        assertFalse(allowed(FlashMode.AUTO, Facing.BACK, true))
    }
}
