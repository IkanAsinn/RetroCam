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
            assertEquals(if (m == FlashMode.FILM) FlashMode.FILM else FlashMode.OFF, FlashLogic.effective(m, Facing.BACK, false))
        }
        assertTrue(FlashLogic.available(Facing.BACK, true))
        assertFalse(FlashLogic.available(Facing.BACK, false))
        assertTrue(FlashLogic.available(Facing.FRONT, false))
    }

    @Test
    fun cycleOnFrontIsOffOnFilm() {
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.OFF, Facing.FRONT))
        assertEquals(FlashMode.FILM, FlashLogic.next(FlashMode.ON, Facing.FRONT))
        assertEquals(FlashMode.OFF, FlashLogic.next(FlashMode.FILM, Facing.FRONT))
        assertEquals(FlashMode.FILM, FlashLogic.next(FlashMode.AUTO, Facing.FRONT)) // stored AUTO reads as ON
        assertEquals(FlashMode.ON, FlashLogic.displayMode(FlashMode.AUTO, Facing.FRONT))
        assertEquals(FlashMode.AUTO, FlashLogic.next(FlashMode.OFF, Facing.BACK))
        assertEquals(FlashMode.ON, FlashLogic.next(FlashMode.AUTO, Facing.BACK))
    }

    @Test
    fun filmFlashCyclesPerFacingAndFlashUnit() {
        fun cycle(start: FlashMode, facing: Facing, unit: Boolean, n: Int): List<FlashMode> {
            val out = ArrayList<FlashMode>()
            var m = start
            repeat(n) { m = FlashLogic.next(m, facing, unit); out += m }
            return out
        }
        val o = FlashMode.OFF
        assertEquals(listOf(FlashMode.AUTO, FlashMode.ON, FlashMode.FILM, o), cycle(o, Facing.BACK, true, 4))
        assertEquals(listOf(FlashMode.FILM, o, FlashMode.FILM), cycle(o, Facing.BACK, false, 3))
        assertEquals(listOf(FlashMode.ON, FlashMode.FILM, o), cycle(o, Facing.FRONT, false, 3))
        // a stored AUTO/ON without a unit reads as OFF, so the next tap goes to FILM
        assertEquals(FlashMode.FILM, FlashLogic.next(FlashMode.ON, Facing.BACK, false))
        assertEquals(o, FlashLogic.displayMode(FlashMode.AUTO, Facing.BACK, false))
        assertEquals(FlashMode.FILM, FlashLogic.displayMode(FlashMode.FILM, Facing.BACK, false))
        assertTrue(FlashLogic.toggleEnabled())
    }

    @Test
    fun filmFlashIsNeverARealOrScreenFlash() {
        for (facing in Facing.entries) for (unit in listOf(true, false)) {
            assertEquals(FlashMode.FILM, FlashLogic.effective(FlashMode.FILM, facing, unit))
            assertFalse(FlashLogic.usesScreenFlash(FlashMode.FILM, facing))
        }
        assertTrue(FlashLogic.isFilm(FlashMode.FILM))
        assertFalse(FlashLogic.isFilm(FlashMode.ON))
    }

    @Test
    fun aeLockIsAllowedWithFilmFlash() {
        for (facing in Facing.entries) for (unit in listOf(true, false)) {
            assertTrue(ExposureLogic.aeLockAllowed(FlashMode.FILM, FlashLogic.available(facing, unit)))
        }
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
