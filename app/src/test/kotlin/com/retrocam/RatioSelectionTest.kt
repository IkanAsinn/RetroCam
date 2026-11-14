package com.retrocam

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.retrocam.data.Settings
import com.retrocam.data.cycleNext
import com.retrocam.data.toSettings
import com.retrocam.data.writeSettings
import com.retrocam.filmengine.FrameFormat
import com.retrocam.filmengine.PhotoRatio
import com.retrocam.logic.FrameSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatioSelectionTest {
    @Test
    fun defaultIsFullSensorFrame() {
        assertEquals(PhotoRatio.R4_3, Settings().ratio)
    }

    @Test
    fun cyclesInTheDocumentedOrder() {
        assertEquals(
            listOf(PhotoRatio.R3_2, PhotoRatio.R16_9, PhotoRatio.R1_1, PhotoRatio.R4_3),
            generateSequence(PhotoRatio.R4_3) { it.cycleNext() }.drop(1).take(4).toList(),
        )
    }

    @Test
    fun fixedFormatCamerasIgnoreTheRatio() {
        val fixed = listOf(
            FrameFormat.SQUARE to true, FrameFormat.SQUARE to false,
            FrameFormat.HALF_FRAME to true, FrameFormat.HALF_FRAME to false,
            FrameFormat.INSTANT_SQUARE to true, FrameFormat.INSTANT_SQUARE to false,
            FrameFormat.INSTANT_MINI to true,
        )
        for ((frame, option) in fixed) for (ratio in PhotoRatio.entries) {
            assertTrue("$frame/$option locked", FrameSelection.isRatioLocked(frame, option))
            assertEquals(PhotoRatio.R4_3, FrameSelection.effectiveRatio(frame, option, ratio))
        }
        assertEquals(1f, FrameSelection.previewMaskAspect(FrameFormat.SQUARE, true, PhotoRatio.R16_9))
        assertEquals(1f, FrameSelection.previewMaskAspect(FrameFormat.INSTANT_SQUARE, false, PhotoRatio.R3_2))
        assertNull(FrameSelection.previewMaskAspect(FrameFormat.HALF_FRAME, true, PhotoRatio.R16_9))
        assertNull(FrameSelection.previewMaskAspect(FrameFormat.INSTANT_MINI, true, PhotoRatio.R1_1))
    }

    @Test
    fun normalCamerasAndInstantMiniWithoutBorderUseTheRatio() {
        for ((frame, option) in listOf(FrameFormat.FULL to true, FrameFormat.FULL to false, FrameFormat.INSTANT_MINI to false)) {
            assertFalse("$frame/$option", FrameSelection.isRatioLocked(frame, option))
            for (ratio in PhotoRatio.entries) assertEquals(ratio, FrameSelection.effectiveRatio(frame, option, ratio))
            assertNull(FrameSelection.previewMaskAspect(frame, option, PhotoRatio.R4_3))
            assertEquals(2f / 3f, FrameSelection.previewMaskAspect(frame, option, PhotoRatio.R3_2)!!, 1e-6f)
            assertEquals(9f / 16f, FrameSelection.previewMaskAspect(frame, option, PhotoRatio.R16_9)!!, 1e-6f)
            assertEquals(1f, FrameSelection.previewMaskAspect(frame, option, PhotoRatio.R1_1)!!, 1e-6f)
        }
    }

    @Test
    fun ratioRoundTripsThroughPreferences() {
        for (ratio in PhotoRatio.entries) {
            val s = Settings(ratio = ratio)
            val prefs = mutablePreferencesOf().also { it.writeSettings(s) }
            assertEquals(s, prefs.toSettings())
        }
    }

    @Test
    fun unknownStoredRatioFallsBackToDefault() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("photo_ratio") to "R21_9")
        assertEquals(PhotoRatio.R4_3, prefs.toSettings().ratio)
    }
}
