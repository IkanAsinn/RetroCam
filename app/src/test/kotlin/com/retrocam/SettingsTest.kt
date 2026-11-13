package com.retrocam

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.data.Settings
import com.retrocam.data.TimerSetting
import com.retrocam.data.cycleNext
import com.retrocam.data.toSettings
import com.retrocam.data.writeSettings
import com.retrocam.filmengine.DateStampFormat
import com.retrocam.profiles.FlareMode
import com.retrocam.profiles.GrainLevel
import com.retrocam.profiles.LeakMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun defaultsFollowThePrd() {
        val s = Settings()
        assertFalse("date stamp must be off by default", s.dateStamp)
        assertFalse("EXIF location must be opt-in", s.exifLocation)
        assertFalse(s.zoomUnlocked)
        assertTrue(s.shutterSound)
        assertTrue(s.frameOption)
        assertEquals(TimerSetting.OFF, s.timer)
        assertEquals(FlashMode.OFF, s.flash)
        assertEquals(Facing.BACK, s.facing)
        assertEquals(GrainLevel.NORMAL, s.grain)
        assertNull(s.cameraId)
        assertEquals(DateStampFormat.YY_MM_DD, s.dateFormat)
    }

    @Test
    fun emptyStoreGivesDefaults() {
        assertEquals(Settings(), emptyPreferences().toSettings())
    }

    @Test
    fun everyFieldRoundTripsThroughPreferences() {
        val s = Settings(
            cameraId = "gold_snap", flash = FlashMode.ON, grain = GrainLevel.HIGH, flare = FlareMode.ALWAYS,
            leaks = LeakMode.STRONG, dateStamp = true, dateFormat = DateStampFormat.DD_MM_YY, timer = TimerSetting.TEN,
            shutterSound = false, exifLocation = true, zoomUnlocked = true, facing = Facing.FRONT, frameOption = false,
        )
        val prefs = mutablePreferencesOf().also { it.writeSettings(s) }
        assertEquals(s, prefs.toSettings())
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("grain") to "EXTREME", stringPreferencesKey("timer") to "")
        val s = prefs.toSettings()
        assertEquals(GrainLevel.NORMAL, s.grain)
        assertEquals(TimerSetting.OFF, s.timer)
    }

    @Test
    fun cycleOrderOfEveryToggle() {
        assertEquals(listOf(FlashMode.AUTO, FlashMode.ON, FlashMode.OFF), cycle(FlashMode.OFF))
        assertEquals(listOf(GrainLevel.LOW, GrainLevel.NORMAL, GrainLevel.HIGH, GrainLevel.OFF), cycle(GrainLevel.OFF, 4))
        assertEquals(listOf(FlareMode.AUTO, FlareMode.ALWAYS, FlareMode.OFF), cycle(FlareMode.OFF))
        assertEquals(listOf(LeakMode.SUBTLE, LeakMode.STRONG, LeakMode.OFF), cycle(LeakMode.OFF))
        assertEquals(listOf(TimerSetting.THREE, TimerSetting.TEN, TimerSetting.OFF), cycle(TimerSetting.OFF))
        assertEquals(listOf(0, 3, 10), TimerSetting.values().map { it.seconds })
    }

    private inline fun <reified E : Enum<E>> cycle(start: E, steps: Int = 3): List<E> {
        val out = mutableListOf<E>()
        var v = start
        repeat(steps) { v = v.cycleNext(); out += v }
        return out
    }
}
