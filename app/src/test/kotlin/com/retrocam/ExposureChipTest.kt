package com.retrocam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.retrocam.camera.ExposureInfo
import com.retrocam.logic.ExposureLogic
import com.retrocam.ui.ExposureChip
import com.retrocam.ui.ExposureRow
import com.retrocam.ui.ZoomChips
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The EV chip shows the formatted value and tapping it opens the slider row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class ExposureChipTest {
    @get:Rule val rule = createComposeRule()

    private val info = ExposureInfo(true, -12, 12, 1f / 3f)
    private var index by mutableIntStateOf(2)
    private var open by mutableStateOf(false)
    private var locked by mutableStateOf(false)

    private fun setContent() = rule.setContent {
        Column {
            Box {
                if (open) {
                    ExposureRow(
                        info = info,
                        index = index,
                        onIndexChange = { index = it },
                        aeLocked = locked,
                        aeLockEnabled = true,
                        onToggleLock = { locked = !locked },
                        onInteraction = {},
                        rotation = { 0f },
                    )
                }
            }
            ZoomChips(
                stops = emptyList(),
                selectedIndex = -1,
                rotation = { 0f },
                onSelect = {},
                exposure = ExposureChip(ExposureLogic.format(info, index), index != 0),
                onExposure = { open = !open },
            )
        }
    }

    @Test fun chipShowsFormattedValueAndOpensSlider() {
        setContent()
        rule.onNodeWithText("EV +0.7").assertIsDisplayed()
        rule.onNodeWithTag("exposure_slider").assertDoesNotExist()
        rule.onNode(hasContentDescription("Exposure compensation +0.7. Tap to adjust.")).performClick()
        rule.onNodeWithTag("exposure_slider").assertIsDisplayed()
        rule.onNodeWithText("+0.7").assertIsDisplayed()
    }

    @Test fun negativeValueUsesRealMinus() {
        index = -4
        setContent()
        rule.onNodeWithText("EV −1.3").assertIsDisplayed()
    }

    @Test fun lockToggleCallsBack() {
        open = true
        setContent()
        rule.onNode(hasContentDescription("AE/AF lock is off. Tap to lock.")).performClick()
        assertEquals(true, locked)
        rule.onNode(hasContentDescription("AE/AF lock is on. Tap to unlock.")).assertIsDisplayed()
    }
}
