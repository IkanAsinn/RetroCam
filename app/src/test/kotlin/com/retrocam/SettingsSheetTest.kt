package com.retrocam

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.retrocam.data.Settings
import com.retrocam.ui.SettingsActions
import com.retrocam.ui.SettingsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sheet's content must not move or resize across layout passes (the "panel keeps going up" feedback loop). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class SettingsSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun contentIsStableAcrossFramesAndShowsTheMirrorRow() {
        rule.setContent {
            SettingsSheet(Settings(), "0.0", SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
        }
        rule.waitForIdle()
        val title = rule.onNodeWithText("Settings")
        val first = title.getBoundsInRoot()
        repeat(3) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        val later = title.getBoundsInRoot()
        assertEquals(first.top, later.top)
        assertEquals(first.bottom, later.bottom)
        assertTrue(later.top.value >= 0f)
        rule.onNodeWithText("Mirror front camera photos").assertExists()
    }
}
