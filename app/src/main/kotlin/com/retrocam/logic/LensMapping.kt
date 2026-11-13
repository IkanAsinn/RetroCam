package com.retrocam.logic

import com.retrocam.camera.ActiveLens
import com.retrocam.profiles.LensKind

object LensMapping {
    fun toLensKind(lens: ActiveLens): LensKind = when (lens) {
        ActiveLens.ULTRA_WIDE -> LensKind.ULTRA_WIDE
        ActiveLens.MAIN -> LensKind.MAIN
        ActiveLens.TELE -> LensKind.TELE
        ActiveLens.FRONT -> LensKind.FRONT
    }
}
