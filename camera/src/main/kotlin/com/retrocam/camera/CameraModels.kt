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
)

/** Upright bitmap (sensor rotation already applied, front camera NOT mirrored). */
data class CapturedFrame(
    val bitmap: Bitmap,
    val flashFired: Boolean,
    val lens: ActiveLens,
    val timestampMillis: Long,
)
