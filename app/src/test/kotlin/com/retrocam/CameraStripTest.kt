package com.retrocam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpRect
import com.retrocam.filmengine.FrameFormat
import com.retrocam.profiles.CameraFamily
import com.retrocam.profiles.CameraProfile
import com.retrocam.profiles.CardBody
import com.retrocam.profiles.CardSpec
import com.retrocam.profiles.FlareStyleSpec
import com.retrocam.profiles.GrainSpec
import com.retrocam.profiles.HalationSpec
import com.retrocam.profiles.VignetteSpec
import com.retrocam.ui.CameraStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Regression for "camera selection always runs to the right until the last card":
 * programmatic scrolls must never feed back into onSelect, and a user scroll must select the card that is really centred.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CameraStripTest {
    @get:Rule val rule = createComposeRule()

    private fun profile(i: Int) = CameraProfile(
        id = "cam_$i", name = "Cam $i", family = CameraFamily.COMPACT, release = "1.0",
        exposureEv = 0f, blackLift = 0f, contrast = 1f,
        grain = GrainSpec(0.4f, 1.5f), vignette = VignetteSpec(0.3f, 0.5f),
        halation = HalationSpec(0f, "#FF3300"), chromaticAberration = 0f, edgeSoftness = 0f,
        flash = null, flareStyle = FlareStyleSpec.NONE, frame = FrameFormat.FULL,
        dateStampColor = "#F28C28", card = CardSpec(CardBody.COMPACT, "#2B2733", "#E2683A"),
    )

    private val profiles = (0 until 20).map(::profile)

    private var selected by mutableIntStateOf(3)
    private val selections = mutableListOf<Int>()

    private fun setContent() {
        rule.setContent {
            Box(Modifier.width(400.dp)) {
                CameraStrip(
                    profiles = profiles,
                    selectedIndex = selected,
                    onSelect = { selections += it; selected = it }, // like the ViewModel: selection echoes back
                    rotation = { 0f },
                    modifier = Modifier.testTag("strip"),
                )
            }
        }
    }

    private fun card(index: Int, isSelected: Boolean) = hasContentDescription(
        if (isSelected) "Cam $index camera, selected" else "Cam $index camera",
    )

    private fun centreOffset(index: Int): Float {
        val strip = rule.onNodeWithTag("strip").getBoundsInRoot()
        val c = rule.onNode(card(index, true)).getBoundsInRoot()
        return abs(centreX(c) - centreX(strip))
    }

    private fun centreX(b: DpRect) = (b.left.value + b.right.value) / 2f

    @Test
    fun initialSelectionIsStableAndCentred() {
        setContent()
        rule.waitForIdle()
        assertTrue("onSelect was called: $selections", selections.isEmpty())
        assertEquals(3, selected)
        assertTrue("selected card off-centre by ${centreOffset(3)}dp", centreOffset(3) < 0.5f)
    }

    @Test
    fun programmaticChangeSettlesOnTheCardWithoutFeedback() {
        setContent()
        rule.waitForIdle()
        selected = 7
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertTrue("onSelect was called: $selections", selections.isEmpty())
        assertEquals(7, selected)
        assertTrue("card 7 off-centre by ${centreOffset(7)}dp", centreOffset(7) < 0.5f)
    }

    @Test
    fun tappingAnotherCardSelectsExactlyThatCard() {
        setContent()
        rule.waitForIdle()
        rule.onNode(card(4, false)).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(listOf(4), selections)
        assertEquals(4, selected)
        assertTrue(centreOffset(4) < 0.5f)
    }

    @Test
    fun userSwipeSelectsTheCentredCardAndStops() {
        setContent()
        rule.waitForIdle()
        rule.onNodeWithTag("strip").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        assertTrue("swipe left must move forward: $selected", selected > 3)
        assertTrue("selection ran away to the end: $selected", selected < 19)
        assertEquals("exactly one user selection expected: $selections", 1, selections.size)
        assertEquals(selected, selections.single())
        assertTrue("card $selected off-centre by ${centreOffset(selected)}dp", centreOffset(selected) < 0.5f)

        val after = selected
        rule.onNodeWithTag("strip").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        assertTrue("swipe right must move back: $selected vs $after", selected < after)
        assertTrue(centreOffset(selected) < 0.5f)
    }
}
