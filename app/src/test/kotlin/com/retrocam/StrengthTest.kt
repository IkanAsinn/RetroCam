package com.retrocam

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.retrocam.data.Settings
import com.retrocam.data.toSettings
import com.retrocam.data.writeSettings
import com.retrocam.logic.StrengthMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {
    @Test
    fun percentMapsToUnitRangeAndClamps() {
        assertEquals(0f, StrengthMath.toStrength(0), 0f)
        assertEquals(0.8f, StrengthMath.toStrength(80), 1e-6f)
        assertEquals(1f, StrengthMath.toStrength(100), 0f)
        assertEquals(0f, StrengthMath.toStrength(-20), 0f)
        assertEquals(1f, StrengthMath.toStrength(250), 0f)
    }

    @Test
    fun snapRoundsToFiveAndClamps() {
        assertEquals(80, StrengthMath.snap(79f))
        assertEquals(75, StrengthMath.snap(77.4f))
        assertEquals(0, StrengthMath.snap(-9f))
        assertEquals(100, StrengthMath.snap(140f))
    }

    @Test
    fun defaultsToFullStrengthForEveryProfile() {
        val s = Settings()
        assertEquals(100, s.strengthFor("gold_snap"))
        assertEquals(100, s.strengthFor(null))
        assertTrue(s.strengthPercent.isEmpty())
    }

    @Test
    fun strengthIsPerProfileAndFullStrengthIsNotStored() {
        val s = Settings().withStrength("a", 80).withStrength("b", 35)
        assertEquals(80, s.strengthFor("a"))
        assertEquals(35, s.strengthFor("b"))
        assertEquals(100, s.strengthFor("c"))
        val back = s.withStrength("a", 100)
        assertFalse(back.strengthPercent.containsKey("a"))
        assertEquals(100, back.withStrength("a", 500).strengthFor("a"))
        assertEquals(0, s.withStrength("a", -5).strengthFor("a"))
    }

    @Test
    fun strengthRoundTripsThroughPreferences() {
        val s = Settings(cameraId = "a", strengthPercent = mapOf("a" to 80, "b_c" to 0))
        val prefs = mutablePreferencesOf().also { it.writeSettings(s) }
        assertEquals(s, prefs.toSettings())
        prefs.writeSettings(s.copy(strengthPercent = emptyMap()))
        assertEquals(emptyMap<String, Int>(), prefs.toSettings().strengthPercent)
    }

    @Test
    fun corruptStoredStrengthIsIgnoredOrClamped() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("strength_pct") to "a:abc;;:5;b:250;c:40;novalue;d:-3")
        val m = prefs.toSettings().strengthPercent
        // a: unparsable, :5 and novalue skipped, b: 250 clamps to 100 (not stored), d: -3 clamps to 0
        assertEquals(mapOf("c" to 40, "d" to 0), m)
    }
}
