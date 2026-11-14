package com.retrocam.logic

import com.retrocam.camera.Facing

/** Decides whether a saved photo is flipped to match the mirrored selfie viewfinder. */
object MirrorLogic {
    /** Only front-camera shots, and only when the user turned the setting on. */
    fun shouldMirror(settingOn: Boolean, facing: Facing): Boolean = settingOn && facing == Facing.FRONT
}
