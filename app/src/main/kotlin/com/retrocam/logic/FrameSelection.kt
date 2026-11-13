package com.retrocam.logic

import com.retrocam.filmengine.FrameFormat

/** Which frame the finished photo gets, and which aspect the viewfinder previews. */
object FrameSelection {
    /**
     * Half Frame always keeps its layout and square types (Plastic 120) keep their crop.
     * Instant types get the white border only while the "frame" option is on; without it
     * they fall back to the plain crop that matches the viewfinder (square for Instant
     * Square, full frame for Instant Mini).
     */
    fun choose(profileFrame: FrameFormat, frameOptionEnabled: Boolean): FrameFormat = when (profileFrame) {
        FrameFormat.HALF_FRAME -> FrameFormat.HALF_FRAME
        FrameFormat.INSTANT_SQUARE -> if (frameOptionEnabled) FrameFormat.INSTANT_SQUARE else FrameFormat.SQUARE
        FrameFormat.INSTANT_MINI -> if (frameOptionEnabled) FrameFormat.INSTANT_MINI else FrameFormat.FULL
        FrameFormat.SQUARE, FrameFormat.FULL -> profileFrame
    }

    /** True when the viewfinder shows a 1:1 crop mask over the 3:4 preview. */
    fun isSquarePreview(profileFrame: FrameFormat, frameOptionEnabled: Boolean): Boolean {
        val f = choose(profileFrame, frameOptionEnabled)
        return f == FrameFormat.SQUARE || f == FrameFormat.INSTANT_SQUARE
    }
}
