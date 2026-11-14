package com.retrocam.camera

import android.graphics.Bitmap

enum class Facing { BACK, FRONT }

enum class FlashMode { OFF, AUTO, ON }

enum class ActiveLens { ULTRA_WIDE, MAIN, TELE, FRONT }

/** e.g. ZoomStop(0.6f, "0.6×"). */
data class ZoomStop(val ratio: Float, val label: String)

data class CameraUiState(
    val facing: Facing,
    val hasFront: Boolean,
    val hasBack: Boolean,
    val hasFlashUnit: Boolean,
    val zoomRatio: Float,
    val minZoom: Float,
    val maxZoom: Float,
    val zoomStops: List<ZoomStop>,
    val lensKind: ActiveLens,
    /** Exposure compensation capabilities of the bound camera; [ExposureInfo.supported] is false when unavailable. */
    val exposure: ExposureInfo = ExposureInfo.UNSUPPORTED,
    /** Exposure compensation index currently applied (already clamped to [exposure]'s range). */
    val exposureIndex: Int = 0,
    /** True while auto exposure (and white balance) is locked. */
    val aeLocked: Boolean = false,
)

/**
 * Exposure-compensation capabilities: index range and step size in EV per index (CameraX reports the step as a
 * Rational, e.g. 1/3 or 1/2). Pure data and maths so it is JVM-testable.
 */
data class ExposureInfo(val supported: Boolean, val minIndex: Int, val maxIndex: Int, val stepEv: Float) {
    /** EV for an index (index * step). */
    fun evFor(index: Int): Float = index * stepEv

    /** Nearest in-range index for [ev]; 0 when unsupported. */
    fun indexFor(ev: Float): Int {
        if (!supported || stepEv <= 0f) return 0
        return Math.round(ev / stepEv).coerceIn(minIndex, maxIndex)
    }

    companion object {
        val UNSUPPORTED = ExposureInfo(false, 0, 0, 0f)
    }
}

/** Upright bitmap (sensor rotation already applied, front camera NOT mirrored). */
data class CapturedFrame(
    val bitmap: Bitmap,
    val flashFired: Boolean,
    val lens: ActiveLens,
    val timestampMillis: Long,
    /** Which camera took the shot; the bitmap itself is never mirrored, the pipeline decides. */
    val facing: Facing = Facing.BACK,
)
