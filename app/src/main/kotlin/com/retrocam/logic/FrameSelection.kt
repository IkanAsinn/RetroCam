package com.retrocam.logic

import com.retrocam.filmengine.FrameFormat
import com.retrocam.filmengine.PhotoRatio

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

    /** True when the camera shoots a fixed format and the user's ratio choice does not apply. */
    fun isRatioLocked(profileFrame: FrameFormat, frameOptionEnabled: Boolean): Boolean =
        choose(profileFrame, frameOptionEnabled) != FrameFormat.FULL

    /** Ratio actually used for the photo: the user's choice for FULL cameras, 4:3 (no crop) for fixed formats. */
    fun effectiveRatio(profileFrame: FrameFormat, frameOptionEnabled: Boolean, chosen: PhotoRatio): PhotoRatio =
        if (isRatioLocked(profileFrame, frameOptionEnabled)) PhotoRatio.R4_3 else chosen

    /**
     * Portrait width/height of the viewfinder crop mask (centred, largest that fits) inside the fixed 3:4 box, or null when the whole box is visible.
     * The mask is the same for every phone orientation: the box is fixed to the sensor, and a 9:16 portrait mask is exactly
     * the 16:9 landscape crop once the phone is turned.
     */
    fun previewMaskAspect(profileFrame: FrameFormat, frameOptionEnabled: Boolean, chosen: PhotoRatio): Float? = when {
        isSquarePreview(profileFrame, frameOptionEnabled) -> 1f
        else -> effectiveRatio(profileFrame, frameOptionEnabled, chosen).takeUnless { it.isFullFrame }?.portraitAspect
    }
}
