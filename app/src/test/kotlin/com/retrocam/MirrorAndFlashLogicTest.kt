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
    fun cycleOnFrontIsOffOn() {
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.OFF, Facing.FRONT))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.ON, Facing.FRONT))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.AUTO, Facing.FRONT)) // stored AUTO reads as ON
        assertEquals(FlashMode.ON, FlashLogic.displayMode(FlashMode.AUTO, Facing.FRONT))
        assertEquals(FlashMode.AUTO, FlashLogic.next(FlashMode.OFF, Facing.BACK))
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.AUTO, Facing.BACK))
    }

    @Test
    fun flashCyclesPerFacingAndFlashUnit() {
        fun cycle(start: FlashMode, facing: Facing, unit: Boolean, n: Int): List<FlashMode> {
            val out = ArrayList<FlashMode>()
            var m = start
            repeat(n) { m = FlashLogic.next(m, facing, unit); out += m }
            return out
        }
        val o = FlashMode.OFF
        assertEquals(listOf(FlashMode.AUTO, FlashMode.ON, o), cycle(o, Facing.BACK, true, 3))
        assertEquals(listOf(FlashMode.ON, o, FlashMode.ON), cycle(o, Facing.FRONT, false, 3))
        // no unit on the back camera: the toggle is disabled, the shown mode is always OFF
        assertEquals(listOf(o, o), cycle(o, Facing.BACK, false, 2))
        assertEquals(o, FlashLogic.next(FlashMode.ON, Facing.BACK, false))
        assertEquals(o, FlashLogic.displayMode(FlashMode.AUTO, Facing.BACK, false))
        assertFalse(FlashLogic.available(Facing.BACK, false))
    }

    @Test
    fun aeLockOnlyDependsOnTheRealFlash() {
        for (facing in Facing.entries) for (unit in listOf(true, false)) {
            assertTrue(ExposureLogic.aeLockAllowed(FlashMode.OFF, FlashLogic.available(facing, unit)))
        }
        assertFalse(ExposureLogic.aeLockAllowed(FlashMode.ON, FlashLogic.available(Facing.BACK, true)))
        assertTrue(ExposureLogic.aeLockAllowed(FlashMode.ON, FlashLogic.available(Facing.BACK, false)))
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
