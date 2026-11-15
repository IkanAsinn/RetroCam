package com.retrocam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.retrocam.ui.TopBar
import com.retrocam.ui.TopBarActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for "only flash appeared on the top bar": the weight modifier was lost inside TooltipBox, so the
 * first toggle took the whole row and pushed the others off-screen; all 9 must fit 360dp without scrolling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class TopBarTest {
    @get:Rule val rule = createComposeRule()

    private val isToggle = SemanticsMatcher("is a button") { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }

    @Test
    fun everyToggleIsVisibleAndInsideA360dpScreen() {
        rule.setContent {
            Box(Modifier.width(360.dp)) {
                TopBar(
                    state = CameraScreenState(),
                    rotation = { 0f },
                    actions = TopBarActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        rule.waitForIdle()
        val nodes = rule.onAllNodes(isToggle)
        val count = nodes.fetchSemanticsNodes().size
        assertEquals("flash, film flash, grain, flare, leaks, date, timer, strength, settings", 9, count)
        for (i in 0 until count) {
            val node = nodes[i]
            node.assertIsDisplayed()
            val b = node.getBoundsInRoot()
            val w = b.right.value - b.left.value
            assertTrue("toggle $i has no width: $b", w > 0f)
            assertTrue("toggle $i narrower than 40dp: $b", w >= 40f - 0.5f)
            assertTrue("toggle $i outside the screen: $b", b.left.value >= 0f && b.right.value <= 360.5f)
        }
    }
}
