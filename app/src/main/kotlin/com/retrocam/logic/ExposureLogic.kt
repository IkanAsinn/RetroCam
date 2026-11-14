package com.retrocam.logic

import com.retrocam.camera.ExposureInfo
import com.retrocam.camera.FlashMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which transient overlay sits on the bottom edge of the viewfinder. Only one at a time. */
enum class Overlay { NONE, STRENGTH, EXPOSURE }

object Overlays {
    /** Tapping the control of [target] opens it (closing any other overlay) or closes it when already open. */
    fun toggle(current: Overlay, target: Overlay): Overlay = if (current == target) Overlay.NONE else target

    /** The overlay that is actually shown: exposure needs a camera that supports compensation. */
    fun visible(requested: Overlay, ready: Boolean, exposureSupported: Boolean): Overlay = when {
        !ready -> Overlay.NONE
        requested == Overlay.EXPOSURE && !exposureSupported -> Overlay.NONE
        else -> requested
    }
}

/** Exposure-compensation helpers: labels, slider snapping, clamping for a (re)bound camera, AE-lock rules. */
object ExposureLogic {
    const val MINUS = '−'

    /** "±0", "+0.7", "−1.3": one decimal, real minus sign. Values that round to zero read "±0". */
    fun format(ev: Float): String {
        val tenths = (ev * 10f).roundToInt()
        if (tenths == 0) return "±0"
        val text = String.format(Locale.ROOT, "%.1f", abs(tenths) / 10f)
        return if (tenths > 0) "+$text" else "$MINUS$text"
    }

    /** Label for a camera [index] (index * step). */
    fun format(info: ExposureInfo, index: Int): String = format(info.evFor(index))

    /** Slider position (any float) -> nearest valid index of [info]. */
    fun snapIndex(position: Float, info: ExposureInfo): Int =
        position.roundToInt().coerceIn(info.minIndex, info.maxIndex)

    /** Number of slider steps strictly between the ends (Material Slider's `steps`). */
    fun sliderSteps(info: ExposureInfo): Int = (info.maxIndex - info.minIndex - 1).coerceAtLeast(0)

    /** The user's EV re-applied to a new camera: nearest in-range index, 0 when unsupported. */
    fun indexAfterRebind(userEv: Float, info: ExposureInfo): Int = info.indexFor(userEv)

    /** EV the user asked for after moving the slider to [index] of [info]. */
    fun evForIndex(info: ExposureInfo, index: Int): Float = info.evFor(index.coerceIn(info.minIndex, info.maxIndex))

    /**
     * AE lock fights the pre-capture metering of the flash, so the lock is only offered with flash OFF
     * (or on a camera without a flash unit, where the flash setting is moot).
     */
    fun aeLockAllowed(flash: FlashMode, hasFlashUnit: Boolean): Boolean = flash == FlashMode.OFF || !hasFlashUnit
}
