package com.retrocam.logic

import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode

/**
 * Flash rules per camera (the REAL flash only; the render-time "film flash" is a separate Settings.filmFlash toggle).
 *  - Back camera with a flash unit: OFF -> AUTO -> ON -> OFF.
 *  - Front camera (the screen is the flash, no AUTO): OFF -> ON -> OFF; a stored AUTO reads as ON.
 *  - Back camera without a unit: the toggle is disabled and the flash is OFF.
 */
object FlashLogic {
    /** True when the flash toggle works: a back camera with a unit, or the front camera (screen flash). */
    fun available(facing: Facing, hasFlashUnit: Boolean): Boolean = facing == Facing.FRONT || hasFlashUnit

    /** The mode the camera should really run with. */
    fun effective(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean): FlashMode = when {
        facing == Facing.FRONT -> if (setting == FlashMode.OFF) FlashMode.OFF else FlashMode.ON
        hasFlashUnit -> setting
        else -> FlashMode.OFF
    }

    /** True when the shot is lit by the screen (front camera, flash on). */
    fun usesScreenFlash(setting: FlashMode, facing: Facing): Boolean =
        facing == Facing.FRONT && setting != FlashMode.OFF

    /** Mode shown by the top-bar toggle: AUTO does not exist on the front camera (reads as ON) nor without a unit (reads as OFF). */
    fun displayMode(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean = true): FlashMode = when {
        setting == FlashMode.OFF -> FlashMode.OFF
        facing == Facing.FRONT -> FlashMode.ON
        hasFlashUnit -> setting
        else -> FlashMode.OFF
    }

    /** Tap-to-cycle, see the class comment. */
    fun next(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean = true): FlashMode {
        val shown = displayMode(setting, facing, hasFlashUnit)
        return when {
            facing == Facing.FRONT -> if (shown == FlashMode.ON) FlashMode.OFF else FlashMode.ON
            hasFlashUnit -> when (shown) {
                FlashMode.OFF -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.OFF
            }
            else -> FlashMode.OFF
        }
    }
}
