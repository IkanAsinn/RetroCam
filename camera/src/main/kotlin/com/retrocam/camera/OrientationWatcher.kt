package com.retrocam.camera

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * OrientationEventListener with hysteresis.
 *
 * The activity is locked to portrait, so this is the only source of truth for how the phone is held.
 * Updates arrive on the main looper.
 */
class OrientationWatcher(context: Context) {
    private val _surfaceRotation = MutableStateFlow(Surface.ROTATION_0)
    private val _uiRotation = MutableStateFlow(0f)

    /** Surface.ROTATION_* for ImageCapture. */
    val surfaceRotation: StateFlow<Int> = _surfaceRotation

    /**
     * How much to rotate icons (degrees, clockwise-positive) so they stay upright: 0, 90, -90, 180
     * modulo 360. The value is a continuous angle chosen on the shortest path from the previous one
     * (so it can be e.g. 270 or -180); feed it straight to an animated rotation.
     */
    val uiRotationDegrees: StateFlow<Float> = _uiRotation

    private var center = 0 // bucket centre: 0, 90, 180, 270 (degrees as reported by Android)

    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return // phone flat: keep the last value
            val next = OrientationMath.nextCenter(center, orientation)
            if (next == center) return
            center = next
            _surfaceRotation.value = OrientationMath.surfaceRotation(next)
            _uiRotation.value = OrientationMath.uiRotation(next, _uiRotation.value)
        }
    }

    fun start() {
        if (listener.canDetectOrientation()) listener.enable()
    }

    fun stop() {
        listener.disable()
    }
}

/** Pure orientation maths, unit-tested. */
object OrientationMath {
    /** Extra degrees past the 45 degree boundary before the bucket flips. */
    const val HYSTERESIS_DEGREES = 20

    /** Smallest signed angle a-b in -180..180. */
    fun angleDiff(a: Int, b: Int): Int {
        var d = ((a - b) % 360 + 360) % 360
        if (d > 180) d -= 360
        return d
    }

    /**
     * Bucket centre (0/90/180/270) after receiving [orientation] (0..359, Android's clockwise
     * device rotation). Only flips once [orientation] is HYSTERESIS_DEGREES past the 45 degree
     * boundary of the current bucket. Negative (unknown) input keeps the current bucket.
     */
    fun nextCenter(currentCenter: Int, orientation: Int): Int {
        if (orientation < 0) return currentCenter
        return if (abs(angleDiff(orientation, currentCenter)) >= 45 + HYSTERESIS_DEGREES) {
            ((orientation / 90f).roundToInt() * 90) % 360
        } else {
            currentCenter
        }
    }

    /**
     * Bucket centre to Surface rotation as in the CameraX docs: device turned 90 degrees clockwise
     * (orientation 90) means Surface.ROTATION_270.
     */
    fun surfaceRotation(center: Int): Int = when (((center % 360) + 360) % 360) {
        90 -> Surface.ROTATION_270
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    /**
     * Icon rotation for a bucket centre: the opposite of the device rotation (device turned 90
     * clockwise: icons turn -90). Returns the angle congruent to that target nearest to [previous].
     */
    fun uiRotation(center: Int, previous: Float): Float {
        val target = -center.toFloat()
        val turns = ((previous - target) / 360f).roundToInt()
        return target + turns * 360f
    }
}
