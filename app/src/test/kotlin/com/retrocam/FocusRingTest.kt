package com.retrocam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import com.retrocam.camera.ExposureInfo
import com.retrocam.ui.Viewfinder
import com.retrocam.ui.ViewfinderActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tap on the viewfinder shows the focus ring with its exposure slider and lock button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class FocusRingTest {
    @get:Rule val rule = createComposeRule()

    private val info = ExposureInfo(true, -12, 12, 1f / 3f)
    private var index by mutableIntStateOf(0)
    private var locked by mutableStateOf(false)
    private var lockTaps = 0
    private var focusTaps = 0
    private var swipes = 0
    private var longPresses = 0
    private var rotationDegrees by mutableStateOf(0f)

    private fun setContent(exposure: ExposureInfo = info) {
        // The ring's fade timer must not run away while the test inspects it.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Viewfinder(
                    surfaceRequest = null,
                    maskAspect = null,
                    halfFrameGuide = false,
                    dateStamp = null,
                    cameraName = null,
                    nameVisible = false,
                    countdown = null,
                    rotation = { rotationDegrees },
                    actions = ViewfinderActions(
                        onTapToFocus = { _, _, _, _ ->
                            focusTaps++
                            locked = false
                        },
                        onPinch = {},
                        onPinchEnd = {},
                        onSwipe = { swipes++ },
                        onCancelTimer = {},
                        onExposureIndex = { index = it },
                        onLongPressFocus = { _, _, _, _ -> longPresses++ },
                    ),
                    aeLocked = locked,
                    onAeLockTap = {
                        lockTaps++
                        locked = !locked
                    },
                    exposure = exposure,
                    exposureIndex = index,
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private val viewfinder get() = rule.onNode(hasContentDescription("Viewfinder", substring = true, ignoreCase = true))

    private fun tapViewfinder() {
        viewfinder.performTouchInput { click(center) }
        rule.waitForIdle()
        repeat(60) { rule.mainClock.advanceTimeByFrame() }
    }

    @Test fun noRingBeforeTheFirstTap() {
        setContent()
        rule.onNodeWithTag("focus_slider").assertDoesNotExist()
        rule.onNodeWithTag("focus_lock_button").assertDoesNotExist()
    }

    @Test fun tapShowsRingSliderAndLockButton() {
        setContent()
        tapViewfinder()
        assertEquals(1, focusTaps)
        rule.onNodeWithTag("focus_slider").assertIsDisplayed()
        rule.onNodeWithTag("focus_lock_button").assertIsDisplayed()
    }

    @Test fun lockButtonCallsTheLockActionAndDoesNotRefocus() {
        setContent()
        tapViewfinder()
        rule.onNodeWithTag("focus_lock_button").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, lockTaps)
        assertEquals(1, focusTaps)
        assertTrue(locked)
        rule.onNodeWithTag("focus_lock_button").assertIsDisplayed()
    }

    @Test fun ringFadesAfterThreeSecondsButNotWhenLocked() {
        setContent()
        tapViewfinder()
        rule.mainClock.advanceTimeBy(3_800)
        rule.onNodeWithTag("focus_lock_button").assertDoesNotExist()

        tapViewfinder()
        rule.onNodeWithTag("focus_lock_button").performClick() // locks
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithTag("focus_lock_button").assertIsDisplayed()
    }

    @Test fun pillSaysAeAfLock() {
        locked = true
        setContent()
        rule.onNodeWithText("AE/AF LOCK").assertIsDisplayed()
    }

    @Test fun draggingTheSliderChangesTheSharedExposure() {
        setContent()
        tapViewfinder()
        rule.onNodeWithTag("focus_slider").performTouchInput { swipeUp() }
        assertTrue("index was $index", index > 0)
    }

    @Test fun verticalDragOnViewfinderWithRingChangesExposureAndDoesNotRefocusOrSwipe() {
        setContent()
        tapViewfinder()
        viewfinder.performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.6f) }
        assertTrue("index was $index", index > 0)
        assertEquals(1, focusTaps)
        assertEquals(0, swipes)
    }

    @Test fun verticalDragWithoutRingDoesNothing() {
        setContent()
        viewfinder.performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.6f) }
        assertEquals(0, index)
        assertEquals(0, focusTaps)
    }

    @Test fun landscapeDragFollowsTheUsersUp() {
        rotationDegrees = 90f
        setContent()
        tapViewfinder()
        // Phone turned 90: the user's up is the screen's right, so a rightward drag brightens.
        viewfinder.performTouchInput { swipe(Offset(width * 0.3f, height * 0.2f), Offset(width * 0.6f, height * 0.2f)) }
        assertTrue("index was $index", index > 0)
        assertEquals(0, swipes)
    }

    @Test fun noSliderWhenExposureUnsupported() {
        setContent(ExposureInfo.UNSUPPORTED)
        tapViewfinder()
        rule.onNodeWithTag("focus_slider").assertDoesNotExist()
        rule.onNodeWithTag("focus_lock_button").assertIsDisplayed()
    }

    @Test fun longPressFocusesAndLocksWithoutATap() {
        setContent()
        viewfinder.performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        rule.waitForIdle()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(1, longPresses)
        assertEquals(0, focusTaps)
        rule.onNodeWithTag("focus_lock_button").assertIsDisplayed()
    }
}
