package com.retrocam.logic

import com.retrocam.camera.ExposureInfo
import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the focus ring's satellites sit, in viewfinder pixels (centres) and which side of the ring the slider took. */
data class RingPlacement(
    val sliderCx: Float,
    val sliderCy: Float,
    /** +1 = the slider is on the user's right of the ring, -1 = on the user's left. */
    val side: Int,
    val lockCx: Float,
    val lockCy: Float,
)

/** What a single-finger drag on the viewfinder means. */
enum class DragKind { UNDECIDED, EXPOSURE, SWIPE }

/** Result of tapping the lock button (or the pill / the toggle in the exposure row). */
enum class LockAction { LOCK, UNLOCK, REFUSED }

/** What to do for a tap or long press on the viewfinder given the current lock state. */
data class FocusPlan(val unlockFirst: Boolean, val lockAfterFocus: Boolean, val refused: Boolean)

/**
 * Pure maths and rules for the focus ring: the exposure slider that sits next to it, the AE/AF lock button, the drag
 * gestures and the lock state transitions. "User frame" means upright for the person holding the phone; the layout
 * itself stays portrait, so with the phone turned the user's up/right map onto other screen axes (see [quadrant]).
 */
object FocusRingLogic {
    /** Ring and slider fade this long after the last interaction (never while locked). */
    const val FADE_MS = 3_000L

    /** How long the EV value stays next to the sun after the last change. */
    const val LABEL_MS = 1_200L

    /** A drag must be this much longer along the user's vertical than across it to count as an exposure drag. */
    const val VERTICAL_BIAS = 1.2f

    /** Rotation in degrees (any continuous angle, as fed to uiRotate) -> 0..3 quarter turns clockwise. */
    fun quadrant(degrees: Float): Int = ((degrees / 90f).roundToInt() % 4 + 4) % 4

    /** The user frame's vector (ux right, uy down) expressed in screen axes: a clockwise turn of [quadrant] quarter turns. */
    fun toScreen(ux: Float, uy: Float, quadrant: Int): Pair<Float, Float> = when (quadrant) {
        1 -> Pair(-uy, ux)
        2 -> Pair(-ux, -uy)
        3 -> Pair(uy, -ux)
        else -> Pair(ux, uy)
    }

    /** Screen-space delta -> amount moved toward the user's up (positive = up). Portrait: -dy; 90 degrees: +dx. */
    fun upDelta(dx: Float, dy: Float, quadrant: Int): Float = when (quadrant) {
        1 -> dx
        2 -> dy
        3 -> -dx
        else -> -dy
    }

    /** Screen-space delta -> movement toward the user's right (positive = right). */
    fun rightDelta(dx: Float, dy: Float, quadrant: Int): Float = when (quadrant) {
        1 -> dy
        2 -> -dx
        3 -> -dy
        else -> dx
    }

    /**
     * Classifies a single-finger gesture that has moved ([dx],[dy]) from where it started. Within [slop] it is
     * undecided (a tap so far). While the ring is visible a mostly-vertical (user frame) drag is exposure; any
     * other drag, or any drag with no ring, is the camera-strip swipe.
     */
    fun classifyDrag(dx: Float, dy: Float, quadrant: Int, ringVisible: Boolean, slop: Float): DragKind {
        if (abs(dx) <= slop && abs(dy) <= slop) return DragKind.UNDECIDED
        if (!ringVisible) return DragKind.SWIPE
        val up = abs(upDelta(dx, dy, quadrant))
        val side = abs(rightDelta(dx, dy, quadrant))
        return if (up >= side * VERTICAL_BIAS) DragKind.EXPOSURE else DragKind.SWIPE
    }

    /** Index after dragging [upPx] (toward the user's up) from [startIndex]; the full range spans [rangePx]. */
    fun indexForDrag(startIndex: Int, upPx: Float, info: ExposureInfo, rangePx: Float): Int {
        if (!info.supported || rangePx <= 0f) return startIndex
        val steps = (info.maxIndex - info.minIndex).toFloat()
        return (startIndex + upPx / rangePx * steps).roundToInt().coerceIn(info.minIndex, info.maxIndex)
    }

    /** Index for a finger at [fractionFromBottom] (0 = bottom/min, 1 = top/max) of the slider track. */
    fun indexForTrack(fractionFromBottom: Float, info: ExposureInfo): Int {
        if (!info.supported) return 0
        val f = fractionFromBottom.coerceIn(0f, 1f)
        return (info.minIndex + f * (info.maxIndex - info.minIndex)).roundToInt().coerceIn(info.minIndex, info.maxIndex)
    }

    /** Inverse of [indexForTrack]: where the sun sits, 0 = bottom, 1 = top. */
    fun fractionForIndex(index: Int, info: ExposureInfo): Float {
        val span = info.maxIndex - info.minIndex
        if (!info.supported || span <= 0) return 0.5f
        return ((index - info.minIndex).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Places the slider beside the ring at ([px],[py]) inside a [width] x [height] box. The slider is [sliderLen] long
     * (user vertical) and [sliderThick] wide; it prefers the user's right and flips to the left near an edge. The lock
     * button ([lockSize]) goes under the ring, or above it when there is no room below. Everything is clamped to keep
     * [margin] from the edges. All values are pixels; [quadrant] comes from [quadrant] of the UI rotation.
     */
    fun place(
        px: Float, py: Float, width: Float, height: Float, quadrant: Int,
        ringRadius: Float, gap: Float, sliderLen: Float, sliderThick: Float, lockSize: Float, margin: Float,
    ): RingPlacement {
        val swap = quadrant % 2 == 1
        val sliderHalfW = (if (swap) sliderLen else sliderThick) / 2f
        val sliderHalfH = (if (swap) sliderThick else sliderLen) / 2f

        val sliderOffset = ringRadius + gap + sliderThick / 2f
        var side = 1
        val (rx, ry) = toScreen(sliderOffset, 0f, quadrant)
        val (lx, ly) = toScreen(-sliderOffset, 0f, quadrant)
        val rightOver = overflow(px + rx, py + ry, sliderHalfW, sliderHalfH, width, height, margin)
        val leftOver = overflow(px + lx, py + ly, sliderHalfW, sliderHalfH, width, height, margin)
        if (rightOver > 0f && leftOver < rightOver) side = -1
        val (sx, sy) = toScreen(side * sliderOffset, 0f, quadrant)
        val sliderCx = clampCentre(px + sx, sliderHalfW, width, margin)
        val sliderCy = clampCentre(py + sy, sliderHalfH, height, margin)

        val lockOffset = ringRadius + gap + lockSize / 2f
        val lockHalf = lockSize / 2f
        val (bx, by) = toScreen(0f, lockOffset, quadrant)
        val (ax, ay) = toScreen(0f, -lockOffset, quadrant)
        val belowOver = overflow(px + bx, py + by, lockHalf, lockHalf, width, height, margin)
        val aboveOver = overflow(px + ax, py + ay, lockHalf, lockHalf, width, height, margin)
        val below = belowOver <= 0f || belowOver <= aboveOver
        val lockCx = clampCentre(if (below) px + bx else px + ax, lockHalf, width, margin)
        val lockCy = clampCentre(if (below) py + by else py + ay, lockHalf, height, margin)
        return RingPlacement(sliderCx, sliderCy, side, lockCx, lockCy)
    }

    private fun clampCentre(c: Float, half: Float, extent: Float, margin: Float): Float {
        val lo = margin + half
        val hi = extent - margin - half
        return if (hi < lo) extent / 2f else c.coerceIn(lo, hi)
    }

    /** How far a box centred at ([cx],[cy]) with half sizes ([hw],[hh]) pokes out of the margin-inset area. */
    private fun overflow(cx: Float, cy: Float, hw: Float, hh: Float, w: Float, h: Float, margin: Float): Float =
        maxOf(0f, margin - (cx - hw)) + maxOf(0f, cx + hw - (w - margin)) +
            maxOf(0f, margin - (cy - hh)) + maxOf(0f, cy + hh - (h - margin))

    // ---------------------------------------------------------------- visibility and lock rules

    /** The ring (with slider and lock button) shows once focused, until it fades, and stays while locked. */
    fun ringVisible(hasPoint: Boolean, locked: Boolean, msSinceInteraction: Long): Boolean =
        hasPoint && (locked || msSinceInteraction < FADE_MS)

    /** AE/AF lock is only offered with flash OFF (or no flash unit): same gate as the exposure row. */
    fun lockAllowed(flash: FlashMode, facing: Facing, hasFlashUnit: Boolean): Boolean =
        ExposureLogic.aeLockAllowed(flash, FlashLogic.available(facing, hasFlashUnit))

    /** Lock button, pill or the row toggle: unlock when locked, else lock when allowed, else refuse (with a message). */
    fun lockButton(locked: Boolean, allowed: Boolean): LockAction = when {
        locked -> LockAction.UNLOCK
        allowed -> LockAction.LOCK
        else -> LockAction.REFUSED
    }

    /** Tap on the viewfinder: a held lock is released first, then focus moves to the new point (like iOS). */
    fun tapPlan(locked: Boolean) = FocusPlan(unlockFirst = locked, lockAfterFocus = false, refused = false)

    /** Long press: focus at the point and lock there; refused (still focuses) when the flash is in the way. */
    fun longPressPlan(locked: Boolean, allowed: Boolean) =
        FocusPlan(unlockFirst = locked, lockAfterFocus = allowed, refused = !allowed)

    /** A rebind (camera, facing, lens, resume) always starts unlocked. */
    fun lockedAfterRebind(): Boolean = false
}
