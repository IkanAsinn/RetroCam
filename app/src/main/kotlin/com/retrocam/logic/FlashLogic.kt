package com.retrocam.logic

import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode

/**
 * Flash rules per camera.
 *  - Back camera with a flash unit: OFF -> AUTO -> ON -> FILM -> OFF.
 *  - Back camera without a unit: OFF -> FILM -> OFF.
 *  - Front camera (the screen is the flash, no AUTO): OFF -> ON -> FILM -> OFF; a stored AUTO reads as ON.
 * FILM is the simulated "film flash": added only when the photo is developed. It never fires a real or screen flash,
 * is always available, and behaves like OFF for AE lock, screen brightening and the shutter blink.
 */
object FlashLogic {
    /** True when a REAL flash exists: a back camera with a unit, or the front camera (screen flash). */
    fun available(facing: Facing, hasFlashUnit: Boolean): Boolean = facing == Facing.FRONT || hasFlashUnit

    /** The flash toggle itself always works: FILM needs no hardware. */
    fun toggleEnabled(): Boolean = true

    fun isFilm(setting: FlashMode): Boolean = setting == FlashMode.FILM

    /** The mode the camera should really run with. */
    fun effective(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean): FlashMode = when {
        setting == FlashMode.FILM -> FlashMode.FILM
        facing == Facing.FRONT -> if (setting == FlashMode.OFF) FlashMode.OFF else FlashMode.ON
        hasFlashUnit -> setting
        else -> FlashMode.OFF
    }

    /** True when the shot is lit by the screen (front camera, real flash on; FILM does not light the screen). */
    fun usesScreenFlash(setting: FlashMode, facing: Facing): Boolean =
        facing == Facing.FRONT && setting != FlashMode.OFF && setting != FlashMode.FILM

    /** Mode shown by the top-bar toggle: AUTO does not exist on the front camera (reads as ON) nor without a unit (reads as OFF). */
    fun displayMode(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean = true): FlashMode = when {
        setting == FlashMode.FILM || setting == FlashMode.OFF -> setting
        facing == Facing.FRONT -> FlashMode.ON
        hasFlashUnit -> setting
        else -> FlashMode.OFF
    }

    /** Tap-to-cycle, see the class comment. */
    fun next(setting: FlashMode, facing: Facing, hasFlashUnit: Boolean = true): FlashMode {
        val shown = displayMode(setting, facing, hasFlashUnit)
        return when {
            facing == Facing.FRONT -> when (shown) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.FILM -> FlashMode.OFF
                else -> FlashMode.FILM
            }
            hasFlashUnit -> when (shown) {
                FlashMode.OFF -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.FILM
                FlashMode.FILM -> FlashMode.OFF
            }
            else -> if (shown == FlashMode.FILM) FlashMode.OFF else FlashMode.FILM
        }
    }
}
