package com.retrocam

import com.retrocam.camera.ActiveLens
import com.retrocam.camera.ZoomStop
import com.retrocam.filmengine.FrameFormat
import com.retrocam.logic.CameraPermissionUi
import com.retrocam.logic.Countdown
import com.retrocam.logic.FrameSelection
import com.retrocam.logic.LensMapping
import com.retrocam.logic.PermissionLogic
import com.retrocam.logic.ReleasableHolder
import com.retrocam.logic.ShutterGate
import com.retrocam.logic.ShutterGate.Decision
import com.retrocam.logic.UiMath
import com.retrocam.profiles.LensKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogicTest {
    // ---- lens mapping
    @Test
    fun lensMappingCoversEveryLens() {
        assertEquals(LensKind.ULTRA_WIDE, LensMapping.toLensKind(ActiveLens.ULTRA_WIDE))
        assertEquals(LensKind.MAIN, LensMapping.toLensKind(ActiveLens.MAIN))
        assertEquals(LensKind.TELE, LensMapping.toLensKind(ActiveLens.TELE))
        assertEquals(LensKind.FRONT, LensMapping.toLensKind(ActiveLens.FRONT))
        assertEquals(ActiveLens.values().size, LensKind.values().size)
    }

    // ---- frame selection
    @Test
    fun instantFramesFollowTheOption() {
        assertEquals(FrameFormat.INSTANT_SQUARE, FrameSelection.choose(FrameFormat.INSTANT_SQUARE, true))
        assertEquals(FrameFormat.SQUARE, FrameSelection.choose(FrameFormat.INSTANT_SQUARE, false))
        assertEquals(FrameFormat.INSTANT_MINI, FrameSelection.choose(FrameFormat.INSTANT_MINI, true))
        assertEquals(FrameFormat.FULL, FrameSelection.choose(FrameFormat.INSTANT_MINI, false))
    }

    @Test
    fun halfFrameAndPlainFramesIgnoreTheOption() {
        for (option in listOf(true, false)) {
            assertEquals(FrameFormat.HALF_FRAME, FrameSelection.choose(FrameFormat.HALF_FRAME, option))
            assertEquals(FrameFormat.SQUARE, FrameSelection.choose(FrameFormat.SQUARE, option))
            assertEquals(FrameFormat.FULL, FrameSelection.choose(FrameFormat.FULL, option))
        }
    }

    @Test
    fun squarePreviewOnlyForSquareOutputs() {
        assertTrue(FrameSelection.isSquarePreview(FrameFormat.SQUARE, true))
        assertTrue(FrameSelection.isSquarePreview(FrameFormat.INSTANT_SQUARE, false))
        assertFalse(FrameSelection.isSquarePreview(FrameFormat.INSTANT_MINI, true))
        assertFalse(FrameSelection.isSquarePreview(FrameFormat.HALF_FRAME, true))
        assertFalse(FrameSelection.isSquarePreview(FrameFormat.FULL, true))
    }

    // ---- countdown
    @Test
    fun countdownTicksOncePerSecondThenFinishes() = runTest {
        val ticks = mutableListOf<Pair<Int, Long>>()
        var done = false
        val job = launch {
            Countdown.run(3) { ticks += it to currentTime }
            done = true
        }
        runCurrent()
        assertEquals(listOf(3 to 0L), ticks)
        advanceTimeBy(2_999); runCurrent()
        assertFalse(done)
        advanceTimeBy(1); runCurrent()
        assertTrue(done)
        assertEquals(listOf(3 to 0L, 2 to 1000L, 1 to 2000L), ticks)
        job.join()
    }

    @Test
    fun cancellingTheCountdownStopsIt() = runTest {
        var finished = false
        val job = launch { Countdown.run(10) {}; finished = true }
        runCurrent()
        advanceTimeBy(4_000)
        job.cancel()
        advanceTimeBy(20_000); runCurrent()
        assertFalse(finished)
    }

    // ---- shutter gate
    @Test
    fun gateAcceptsFirstPressAndRejectsWhileCapturing() {
        val g = ShutterGate()
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(1_000))
        assertEquals(Decision.BUSY_CAPTURING, g.tryBeginCapture(5_000))
    }

    @Test
    fun gateDebouncesFastPresses() {
        val g = ShutterGate(debounceMs = 250)
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(1_000))
        g.captureFinished()
        assertEquals(Decision.TOO_SOON, g.tryBeginCapture(1_100))
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(1_250))
    }

    @Test
    fun saveInFlightDoesNotBlockTheNextShotUntilTheQueueIsFull() {
        val g = ShutterGate(maxProcessing = 2, debounceMs = 0)
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(0)); g.captureFinished()
        assertEquals(1, g.processing)
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(1)); g.captureFinished()
        assertEquals(Decision.QUEUE_FULL, g.tryBeginCapture(2))
        g.processingFinished()
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(3))
    }

    @Test
    fun failedCaptureFreesTheGate() {
        val g = ShutterGate(debounceMs = 0)
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(0))
        g.captureFailed()
        assertEquals(0, g.processing)
        assertEquals(Decision.ACCEPT, g.tryBeginCapture(1))
    }

    @Test
    fun processingCounterNeverGoesNegative() {
        val g = ShutterGate()
        g.processingFinished()
        assertEquals(0, g.processing)
    }

    // ---- releasable holder (renderer lifetime)
    @Test
    fun holderCreatesLazilyAndReleasesWhenIdle() = runTest {
        var created = 0
        val released = mutableListOf<Int>()
        val holder = ReleasableHolder(create = { ++created }, release = { released += it })
        holder.requestRelease()
        assertTrue(released.isEmpty())
        assertEquals(1, holder.use { it })
        assertEquals(1, holder.use { it })
        holder.requestRelease()
        assertEquals(listOf(1), released)
        assertEquals(2, holder.use { it })
    }

    @Test
    fun holderDefersReleaseWhileInUse() = runTest {
        val released = mutableListOf<Int>()
        val holder = ReleasableHolder(create = { 7 }, release = { released += it })
        holder.use {
            holder.requestRelease()
            assertTrue("must not release a renderer that is mid-save", released.isEmpty())
        }
        assertEquals(listOf(7), released)
    }

    // ---- ui math
    @Test
    fun selectedZoomStopIsTheNearest() {
        val stops = listOf(ZoomStop(0.6f, "0.6x"), ZoomStop(1f, "1x"), ZoomStop(2f, "2x"))
        assertEquals(1, UiMath.selectedStop(stops, 1.1f))
        assertEquals(0, UiMath.selectedStop(stops, 0.5f))
        assertEquals(2, UiMath.selectedStop(stops, 4f))
        assertEquals(-1, UiMath.selectedStop(emptyList(), 1f))
    }

    @Test
    fun hexColoursParseWithFallback() {
        val c = UiMath.hexToRgb("#FF8000")
        assertEquals(1f, c.r, 1e-4f)
        assertEquals(128 / 255f, c.g, 1e-4f)
        assertEquals(0f, c.b, 1e-4f)
        assertEquals(UiMath.hexToRgb("nope"), UiMath.hexToRgb("#12"))
    }

    // ---- permission flow
    @Test
    fun permissionScreenStates() {
        assertEquals(CameraPermissionUi.GRANTED, PermissionLogic.decide(true, false, false))
        assertEquals(CameraPermissionUi.RATIONALE, PermissionLogic.decide(false, false, false))
        assertEquals(CameraPermissionUi.ASK_AGAIN, PermissionLogic.decide(false, true, true))
        assertEquals(CameraPermissionUi.OPEN_SETTINGS, PermissionLogic.decide(false, true, false))
    }
}
