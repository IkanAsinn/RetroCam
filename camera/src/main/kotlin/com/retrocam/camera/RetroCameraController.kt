package com.retrocam.camera

import android.content.Context
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RetroCameraController(context: Context) {
    val state: StateFlow<CameraUiState?> = MutableStateFlow(null)

    /** For CameraXViewfinder. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    suspend fun bind(owner: LifecycleOwner, effect: CameraEffect?): Unit = TODO("phase 1")

    fun setFacing(facing: Facing): Unit = TODO("phase 1")

    fun setFlashMode(mode: FlashMode): Unit = TODO("phase 1")

    /** Clamped to the allowed range. */
    fun setZoomRatio(ratio: Float): Unit = TODO("phase 1")

    /** Default limit: longest optical lens, or 2x. */
    fun setFullZoomUnlocked(unlocked: Boolean): Unit = TODO("phase 1")

    fun focusAt(x: Float, y: Float, width: Float, height: Float): Unit = TODO("phase 1")

    fun setTargetRotation(surfaceRotation: Int): Unit = TODO("phase 1")

    suspend fun capture(): CapturedFrame = TODO("phase 1")

    fun unbind(): Unit = TODO("phase 1")
}
