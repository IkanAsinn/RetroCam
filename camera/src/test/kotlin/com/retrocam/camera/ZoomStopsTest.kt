package com.retrocam.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStopsTest {
    private fun labels(min: Float, max: Float, optical: List<Float>, unlocked: Boolean) =
        ZoomStops.compute(min, max, optical, unlocked).map { it.label }

    @Test
    fun singleLensGetsOneAndTwoTimesDigital() {
        assertEquals(listOf("1×", "2×"), labels(1f, 8f, emptyList(), false))
        assertEquals(2f, ZoomStops.maxAllowed(1f, 8f, emptyList(), false), 0f)
    }

    @Test
    fun singleLensWithSmallRangeOnlyOneTimes() {
        assertEquals(listOf("1×"), labels(1f, 1.5f, emptyList(), false))
        assertEquals(1.5f, ZoomStops.maxAllowed(1f, 1.5f, emptyList(), false), 0f)
    }

    @Test
    fun logicalCameraWithUltraWideAndTwoTeles() {
        val stops = ZoomStops.compute(0.6f, 10f, listOf(0.6f, 2f, 3f), false)
        assertEquals(listOf(0.6f, 1f, 2f, 3f), stops.map { it.ratio })
        assertEquals(listOf("0.6×", "1×", "2×", "3×"), stops.map { it.label })
        assertEquals(3f, ZoomStops.maxAllowed(0.6f, 10f, listOf(0.6f, 2f, 3f), false), 0f)
    }

    @Test
    fun unlockedRaisesLimitToDeviceMaxButKeepsOpticalStops() {
        val optical = listOf(0.6f, 3f)
        assertEquals(10f, ZoomStops.maxAllowed(0.6f, 10f, optical, true), 0f)
        assertEquals(listOf("0.6×", "1×", "3×"), labels(0.6f, 10f, optical, true))
    }

    @Test
    fun measuredUltraWideBelowDeviceMinSnapsToMin() {
        // 13mm vs 24mm main = 0.54 -> 0.5; device min 0.6, so the chip reads 0.6x
        val ratio = LensMath.ratio(13f, 24f)
        assertEquals(0.5f, ratio, 0f)
        assertEquals(listOf("0.6×", "1×", "2×"), labels(0.6f, 6f, listOf(ratio), true)) // no tele: 2x digital
    }

    @Test
    fun opticalOutsideDeviceRangeIsDropped() {
        assertEquals(listOf("1×"), labels(1f, 2f, listOf(3f), false))
        assertEquals(listOf("1×", "2×"), labels(1f, 4f, listOf(0.6f, 2f), false))
    }

    @Test
    fun oneTimesAlwaysPresent() {
        assertTrue(labels(0.5f, 4f, listOf(0.5f), false).contains("1×"))
    }

    @Test
    fun frontCameraRange() {
        assertEquals(listOf("1×"), labels(1f, 1f, emptyList(), false))
        assertEquals(listOf("1×", "2×"), labels(1f, 4f, emptyList(), false))
    }

    @Test
    fun labelsRoundToOneDecimalAndDropPointZero() {
        assertEquals("0.6×", ZoomStops.label(0.6f))
        assertEquals("1×", ZoomStops.label(1f))
        assertEquals("1×", ZoomStops.label(1.04f))
        assertEquals("3×", ZoomStops.label(2.96f))
        assertEquals("2.5×", ZoomStops.label(2.5f))
        assertEquals("0.5×", ZoomStops.label(0.5f))
    }

    @Test
    fun opticalRatiosSnapToFriendlyValues() {
        assertEquals(1f, ZoomStops.normalizeOptical(0.98f), 0f)
        assertEquals(3f, ZoomStops.normalizeOptical(3.07f), 0f)
        assertEquals(5f, ZoomStops.normalizeOptical(4.9f), 0f)
        assertEquals(2.5f, ZoomStops.normalizeOptical(2.4f), 0f)
        assertEquals(0.6f, ZoomStops.normalizeOptical(0.58f), 0f)
    }

    @Test
    fun lensKindFromZoomRatio() {
        val optical = listOf(0.6f, 3f)
        assertEquals(ActiveLens.ULTRA_WIDE, ZoomStops.lensKind(0.6f, optical))
        assertEquals(ActiveLens.ULTRA_WIDE, ZoomStops.lensKind(0.9f, optical))
        assertEquals(ActiveLens.MAIN, ZoomStops.lensKind(1f, optical))
        assertEquals(ActiveLens.MAIN, ZoomStops.lensKind(2f, optical))
        assertEquals(ActiveLens.TELE, ZoomStops.lensKind(3f, optical))
        assertEquals(ActiveLens.MAIN, ZoomStops.lensKind(2f, emptyList()))
    }
}
