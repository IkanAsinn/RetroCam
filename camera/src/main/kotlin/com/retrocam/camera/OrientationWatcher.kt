package com.retrocam.camera

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** OrientationEventListener with hysteresis. */
class OrientationWatcher(context: Context) {
    /** Surface.ROTATION_* for ImageCapture. */
    val surfaceRotation: StateFlow<Int> = MutableStateFlow(0)

    /** 0, 90, -90, 180: how much to rotate icons. */
    val uiRotationDegrees: StateFlow<Float> = MutableStateFlow(0f)

    fun start(): Unit = TODO("phase 1")

    fun stop(): Unit = TODO("phase 1")
}
