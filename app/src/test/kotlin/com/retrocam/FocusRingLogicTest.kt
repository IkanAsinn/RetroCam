package com.retrocam

import com.retrocam.camera.ExposureInfo
import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.logic.DragKind
import com.retrocam.logic.FocusRingLogic
import com.retrocam.logic.LockAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRingLogicTest {
    private val info = ExposureInfo(true, -12, 12, 1f / 3f)
    private val slop = 8f

    // ---- rotation

    @Test fun quadrantHandlesContinuousAngles() {
        assertEquals(0, FocusRingLogic.quadrant(0f))
        assertEquals(1, FocusRingLogic.quadrant(90f))
        assertEquals(2, FocusRingLogic.quadrant(180f))
        assertEquals(2, FocusRingLogic.quadrant(-180f))
        assertEquals(3, FocusRingLogic.quadrant(270f))
        assertEquals(3, FocusRingLogic.quadrant(-90f))
        assertEquals(0, FocusRingLogic.quadrant(360f))
        assertEquals(1, FocusRingLogic.quadrant(80f)) // mid-animation
    }

    @Test fun upDeltaFollowsTheUsersUp() {
        // Portrait: finger moves up the screen (dy < 0) = up.
        assertEquals(30f, FocusRingLogic.upDelta(5f, -30f, 0), 0f)
        // Icons turned 90 clockwise: the user's up points to the screen's right.
        assertEquals(30f, FocusRingLogic.upDelta(30f, 5f, 1), 0f)
        // Upside down: up is down the screen.
        assertEquals(30f, FocusRingLogic.upDelta(5f, 30f, 2), 0f)
        // 270: up is the screen's left.
        assertEquals(30f, FocusRingLogic.upDelta(-30f, 5f, 3), 0f)
    }

    @Test fun toScreenMatchesUiRotateDirection() {
        // User's up (0,-1) under a 90 degree clockwise rotation lands on the screen's +x.
        assertEquals(Pair(1f, 0f), FocusRingLogic.toScreen(0f, -1f, 1).let { Pair(it.first, it.second + 0f) }.let { Pair(it.first, 0f) })
        assertEquals(1f, FocusRingLogic.toScreen(0f, -1f, 1).first, 0f)
        assertEquals(-1f, FocusRingLogic.toScreen(0f, -1f, 3).first, 0f)
        assertEquals(1f, FocusRingLogic.toScreen(0f, -1f, 2).second, 0f)
        // The user's right (1,0) under 90 is screen down.
        assertEquals(1f, FocusRingLogic.toScreen(1f, 0f, 1).second, 0f)
    }

    // ---- drag -> EV

    @Test fun dragUpIsBrighterAndSnapsToSteps() {
        // 220px spans 24 steps: 55px = 6 steps.
        assertEquals(6, FocusRingLogic.indexForDrag(0, 55f, info, 220f))
        assertEquals(-6, FocusRingLogic.indexForDrag(0, -55f, info, 220f))
        // Between steps rounds to the nearest.
        assertEquals(1, FocusRingLogic.indexForDrag(0, 6f, info, 220f))
        assertEquals(0, FocusRingLogic.indexForDrag(0, 4f, info, 220f))
    }

    @Test fun dragClampsToRangeAndStartsFromCurrent() {
        assertEquals(12, FocusRingLogic.indexForDrag(10, 500f, info, 220f))
        assertEquals(-12, FocusRingLogic.indexForDrag(-10, -500f, info, 220f))
        assertEquals(8, FocusRingLogic.indexForDrag(2, 55f, info, 220f))
    }

    @Test fun dragIgnoredWhenUnsupported() {
        assertEquals(0, FocusRingLogic.indexForDrag(0, 100f, ExposureInfo.UNSUPPORTED, 220f))
    }

    @Test fun dragWithRotationUsesTheTurnedAxis() {
        // Landscape (quadrant 1): a rightward drag of 55px is up.
        val up = FocusRingLogic.upDelta(55f, 3f, 1)
        assertEquals(6, FocusRingLogic.indexForDrag(0, up, info, 220f))
        // Quadrant 3: leftward is up.
        assertEquals(6, FocusRingLogic.indexForDrag(0, FocusRingLogic.upDelta(-55f, 0f, 3), info, 220f))
        // Quadrant 2: downward is up.
        assertEquals(6, FocusRingLogic.indexForDrag(0, FocusRingLogic.upDelta(0f, 55f, 2), info, 220f))
    }

    @Test fun trackMapping() {
        assertEquals(12, FocusRingLogic.indexForTrack(1f, info))
        assertEquals(-12, FocusRingLogic.indexForTrack(0f, info))
        assertEquals(0, FocusRingLogic.indexForTrack(0.5f, info))
        assertEquals(12, FocusRingLogic.indexForTrack(3f, info))
        assertEquals(0.5f, FocusRingLogic.fractionForIndex(0, info), 0f)
        assertEquals(1f, FocusRingLogic.fractionForIndex(12, info), 0f)
        for (i in -12..12) assertEquals(i, FocusRingLogic.indexForTrack(FocusRingLogic.fractionForIndex(i, info), info))
    }

    // ---- gesture classification

    @Test fun withinSlopIsUndecided() {
        assertEquals(DragKind.UNDECIDED, FocusRingLogic.classifyDrag(3f, -5f, 0, true, slop))
    }

    @Test fun verticalDragWithRingIsExposure() {
        assertEquals(DragKind.EXPOSURE, FocusRingLogic.classifyDrag(2f, -20f, 0, true, slop))
        assertEquals(DragKind.EXPOSURE, FocusRingLogic.classifyDrag(-4f, 20f, 0, true, slop))
    }

    @Test fun horizontalDragIsSwipeEvenWithRing() {
        assertEquals(DragKind.SWIPE, FocusRingLogic.classifyDrag(30f, 4f, 0, true, slop))
        // Diagonal beyond the bias counts as swipe.
        assertEquals(DragKind.SWIPE, FocusRingLogic.classifyDrag(20f, -22f, 0, true, slop))
    }

    @Test fun noRingMeansNoExposureDrag() {
        assertEquals(DragKind.SWIPE, FocusRingLogic.classifyDrag(1f, -40f, 0, false, slop))
    }

    @Test fun landscapeSwapsTheAxes() {
        // Quadrant 1: the user's vertical is the screen's x, so a horizontal drag is exposure and a vertical one a swipe.
        assertEquals(DragKind.EXPOSURE, FocusRingLogic.classifyDrag(30f, 3f, 1, true, slop))
        assertEquals(DragKind.SWIPE, FocusRingLogic.classifyDrag(3f, 30f, 1, true, slop))
        assertEquals(DragKind.EXPOSURE, FocusRingLogic.classifyDrag(-30f, 3f, 3, true, slop))
    }

    // ---- placement

    private fun place(px: Float, py: Float, q: Int = 0) = FocusRingLogic.place(
        px, py, 360f, 480f, q, ringRadius = 36f, gap = 12f, sliderLen = 120f, sliderThick = 40f, lockSize = 40f, margin = 8f,
    )

    @Test fun sliderPrefersTheRight() {
        val p = place(150f, 240f)
        assertEquals(1, p.side)
        assertEquals(150f + 36f + 12f + 20f, p.sliderCx, 0.01f)
        assertEquals(240f, p.sliderCy, 0.01f)
        // Lock button under the ring.
        assertEquals(150f, p.lockCx, 0.01f)
        assertEquals(240f + 36f + 12f + 20f, p.lockCy, 0.01f)
    }

    @Test fun sliderFlipsNearTheRightEdge() {
        val p = place(330f, 240f)
        assertEquals(-1, p.side)
        assertEquals(330f - 68f, p.sliderCx, 0.01f)
    }

    @Test fun sliderStaysOnTheRightNearTheLeftEdge() {
        val p = place(20f, 240f)
        assertEquals(1, p.side)
        assertTrue(p.sliderCx - 20f >= 8f)
    }

    @Test fun sliderClampsVerticallyAtTopAndBottom() {
        val top = place(150f, 10f)
        assertEquals(8f + 60f, top.sliderCy, 0.01f)
        val bottom = place(150f, 475f)
        assertEquals(480f - 8f - 60f, bottom.sliderCy, 0.01f)
    }

    @Test fun lockGoesAboveWhenNoRoomBelow() {
        val p = place(150f, 440f)
        assertTrue(p.lockCy < 440f)
        assertEquals(440f - 68f, p.lockCy, 0.01f)
    }

    @Test fun everythingStaysInsideTheBoxForAnyPointAndRotation() {
        for (q in 0..3) {
            var x = 0f
            while (x <= 360f) {
                var y = 0f
                while (y <= 480f) {
                    val p = place(x, y, q)
                    val halfW = if (q % 2 == 1) 60f else 20f
                    val halfH = if (q % 2 == 1) 20f else 60f
                    assertTrue(p.sliderCx - halfW >= 8f - 0.01f && p.sliderCx + halfW <= 352f + 0.01f)
                    assertTrue(p.sliderCy - halfH >= 8f - 0.01f && p.sliderCy + halfH <= 472f + 0.01f)
                    assertTrue(p.lockCx - 20f >= 8f - 0.01f && p.lockCx + 20f <= 352f + 0.01f)
                    assertTrue(p.lockCy - 20f >= 8f - 0.01f && p.lockCy + 20f <= 472f + 0.01f)
                    y += 40f
                }
                x += 30f
            }
        }
    }

    @Test fun landscapeSliderLiesAlongTheScreenX() {
        // Quadrant 1: the user's right is the screen's down, so the slider sits below the ring and runs horizontally.
        val p = place(180f, 200f, 1)
        assertEquals(1, p.side)
        assertEquals(180f, p.sliderCx, 0.01f)
        assertEquals(200f + 68f, p.sliderCy, 0.01f)
    }

    // ---- visibility and lock state transitions

    @Test fun ringFadesUnlessLocked() {
        assertTrue(FocusRingLogic.ringVisible(true, false, 0))
        assertTrue(FocusRingLogic.ringVisible(true, false, FocusRingLogic.FADE_MS - 1))
        assertFalse(FocusRingLogic.ringVisible(true, false, FocusRingLogic.FADE_MS))
        assertTrue(FocusRingLogic.ringVisible(true, true, 60_000))
        assertFalse(FocusRingLogic.ringVisible(false, true, 0))
    }

    @Test fun lockButtonTransitions() {
        assertEquals(LockAction.LOCK, FocusRingLogic.lockButton(locked = false, allowed = true))
        assertEquals(LockAction.UNLOCK, FocusRingLogic.lockButton(locked = true, allowed = true))
        assertEquals(LockAction.REFUSED, FocusRingLogic.lockButton(locked = false, allowed = false))
        // A held lock can always be released, even after the flash made it "not allowed".
        assertEquals(LockAction.UNLOCK, FocusRingLogic.lockButton(locked = true, allowed = false))
    }

    @Test fun tapElsewhereUnlocksAndRefocuses() {
        assertTrue(FocusRingLogic.tapPlan(locked = true).unlockFirst)
        assertFalse(FocusRingLogic.tapPlan(locked = false).unlockFirst)
        assertFalse(FocusRingLogic.tapPlan(locked = true).lockAfterFocus)
    }

    @Test fun longPressFocusesThenLocksWhenAllowed() {
        val ok = FocusRingLogic.longPressPlan(locked = false, allowed = true)
        assertTrue(ok.lockAfterFocus)
        assertFalse(ok.unlockFirst)
        assertFalse(ok.refused)
        val relock = FocusRingLogic.longPressPlan(locked = true, allowed = true)
        assertTrue(relock.unlockFirst && relock.lockAfterFocus)
        val gated = FocusRingLogic.longPressPlan(locked = false, allowed = false)
        assertFalse(gated.lockAfterFocus)
        assertTrue(gated.refused)
    }

    @Test fun rebindAlwaysUnlocks() {
        assertFalse(FocusRingLogic.lockedAfterRebind())
    }

    @Test fun lockIsGatedByFlash() {
        assertTrue(FocusRingLogic.lockAllowed(FlashMode.OFF, Facing.BACK, true))
        assertFalse(FocusRingLogic.lockAllowed(FlashMode.ON, Facing.BACK, true))
        assertFalse(FocusRingLogic.lockAllowed(FlashMode.AUTO, Facing.BACK, true))
        // No flash unit on the back camera: the flash setting is moot.
        assertTrue(FocusRingLogic.lockAllowed(FlashMode.ON, Facing.BACK, false))
        // Front camera uses the screen as flash, so it is gated like a real flash.
        assertFalse(FocusRingLogic.lockAllowed(FlashMode.ON, Facing.FRONT, false))
        assertTrue(FocusRingLogic.lockAllowed(FlashMode.OFF, Facing.FRONT, false))
    }
}
