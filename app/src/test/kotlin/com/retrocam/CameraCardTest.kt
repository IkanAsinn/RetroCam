package com.retrocam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.retrocam.filmengine.FrameFormat
import com.retrocam.profiles.CameraFamily
import com.retrocam.profiles.CameraProfile
import com.retrocam.profiles.CardBody
import com.retrocam.profiles.CardSpec
import com.retrocam.profiles.FlareStyleSpec
import com.retrocam.profiles.GrainSpec
import com.retrocam.profiles.HalationSpec
import com.retrocam.profiles.VignetteSpec
import com.retrocam.ui.CameraCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression for the landscape label running out of the card ("reen Sn"): the whole face turns inside a square. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CameraCardTest {
    @get:Rule val rule = createComposeRule()

    private val profile = CameraProfile(
        id = "long", name = "Green Snapshot Long Name", family = CameraFamily.COMPACT, release = "1.0",
        exposureEv = 0f, blackLift = 0f, contrast = 1f,
        grain = GrainSpec(0.4f, 1.5f), vignette = VignetteSpec(0.3f, 0.5f),
        halation = HalationSpec(0f, "#FF3300"), chromaticAberration = 0f, edgeSoftness = 0f,
        flash = null, flareStyle = FlareStyleSpec.NONE, frame = FrameFormat.FULL,
        dateStampColor = "#F28C28", card = CardSpec(CardBody.COMPACT, "#2B2733", "#E2683A"),
    )

    private fun inside(inner: DpRect, outer: DpRect) =
        inner.left.value >= outer.left.value - 0.5f && inner.top.value >= outer.top.value - 0.5f &&
            inner.right.value <= outer.right.value + 0.5f && inner.bottom.value <= outer.bottom.value + 0.5f

    private fun check(rotation: Float) {
        rule.setContent {
            Box(Modifier.width(200.dp)) { CameraCard(profile, true, { rotation }, strengthPercent = 55) {} }
        }
        rule.waitForIdle()
        val card = rule.onNode(hasContentDescription("Green Snapshot Long Name camera, selected")).getBoundsInRoot()
        for (tag in listOf("cameraFace", "cameraLabel", "strengthBadge")) {
            val b = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$tag at $rotation deg: $b not inside card $card", inside(b, card))
        }
        val label = rule.onNodeWithTag("cameraLabel", useUnmergedTree = true).getBoundsInRoot()
        val w = label.right.value - label.left.value
        val h = label.bottom.value - label.top.value
        // boundsInRoot must include the graphicsLayer rotation: a one-line label is wide upright and tall on its end.
        if (rotation == 0f || rotation == 180f) assertTrue("label should be wider than tall: $label", w > h)
        else assertTrue("rotation not reflected in bounds: $label", h > w)
    }

    @Test fun upright() = check(0f)
    @Test fun landscapeLeft() = check(90f)
    @Test fun landscapeRight() = check(-90f)
    @Test fun upsideDown() = check(180f)
}
