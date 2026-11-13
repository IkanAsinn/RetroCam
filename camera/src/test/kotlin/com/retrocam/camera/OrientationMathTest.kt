package com.retrocam.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrientationMathTest {
    @Test
    fun surfaceRotationMatchesCameraXConvention() {
        assertEquals(Surface.ROTATION_0, OrientationMath.surfaceRotation(0))
        assertEquals(Surface.ROTATION_270, OrientationMath.surfaceRotation(90)) // turned clockwise
        assertEquals(Surface.ROTATION_180, OrientationMath.surfaceRotation(180))
        assertEquals(Surface.ROTATION_90, OrientationMath.surfaceRotation(270)) // turned counter-clockwise
    }

    @Test
    fun staysInBucketUntilTwentyDegreesPastBoundary() {
        assertEquals(0, OrientationMath.nextCenter(0, 44))
        assertEquals(0, OrientationMath.nextCenter(0, 50)) // past 45 but inside hysteresis
        assertEquals(0, OrientationMath.nextCenter(0, 64))
        assertEquals(90, OrientationMath.nextCenter(0, 65))
        assertEquals(0, OrientationMath.nextCenter(0, 296)) // -64
        assertEquals(270, OrientationMath.nextCenter(0, 295)) // -65
    }

    @Test
    fun hysteresisIsSymmetricComingBack() {
        assertEquals(90, OrientationMath.nextCenter(90, 26))
        assertEquals(0, OrientationMath.nextCenter(90, 25))
    }

    @Test
    fun jumpsDirectlyToUpsideDown() {
        assertEquals(180, OrientationMath.nextCenter(0, 175))
        assertEquals(0, OrientationMath.nextCenter(180, 5))
    }

    @Test
    fun wrapAroundNearZero() {
        assertEquals(0, OrientationMath.nextCenter(270, 350))
        assertEquals(0, OrientationMath.nextCenter(90, 10))
        assertEquals(0, OrientationMath.nextCenter(0, 359))
    }

    @Test
    fun unknownOrientationKeepsBucket() {
        assertEquals(90, OrientationMath.nextCenter(90, -1))
    }

    @Test
    fun uiRotationCounterRotatesAgainstDevice() {
        assertEquals(0f, OrientationMath.uiRotation(0, 0f), 0f)
        assertEquals(-90f, OrientationMath.uiRotation(90, 0f), 0f)
        assertEquals(90f, OrientationMath.uiRotation(270, 0f), 0f)
        assertEquals(180f, abs(OrientationMath.uiRotation(180, 0f)), 0f)
    }

    @Test
    fun uiRotationTakesShortestPath() {
        // icons at 180, device now turned 90 clockwise (target -90): continue 180 -> 270
        assertEquals(270f, OrientationMath.uiRotation(90, 180f), 0f)
        // icons at -90, device now upside down: -90 -> -180 (90 deg), never +180 via 270
        assertEquals(-180f, OrientationMath.uiRotation(180, -90f), 0f)
        assertEquals(0f, OrientationMath.uiRotation(0, 90f), 0f)
        // icons at 270 (continuous) back to upright: forward 90 to 360
        assertEquals(360f, OrientationMath.uiRotation(0, 270f), 0f)
        // no step is ever more than 180 degrees
        for (center in listOf(0, 90, 180, 270)) {
            for (prev in listOf(-540f, -270f, -180f, -90f, 0f, 90f, 180f, 270f, 360f)) {
                assertTrue(abs(OrientationMath.uiRotation(center, prev) - prev) <= 180f)
            }
        }
    }
}
