package com.retrocam.logic

import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.data.cycleNext

/**
 * Flash rules per camera. The back camera uses its flash unit (OFF / AUTO / ON). The front camera has no unit and
 * uses the screen as the flash (OFF / ON only): a stored AUTO is treated as ON there.
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

    /** Mode shown by the top-bar toggle: on the front camera AUTO does not exist and reads as ON. */
    fun displayMode(setting: FlashMode, facing: Facing): FlashMode =
        if (facing == Facing.FRONT && setting == FlashMode.AUTO) FlashMode.ON else setting

    /** Tap-to-cycle: front toggles OFF <-> ON, back keeps OFF -> AUTO -> ON. */
    fun next(setting: FlashMode, facing: Facing): FlashMode =
        if (facing == Facing.FRONT) {
            if (displayMode(setting, facing) == FlashMode.ON) FlashMode.OFF else FlashMode.ON
        } else {
            setting.cycleNext()
        }
}
