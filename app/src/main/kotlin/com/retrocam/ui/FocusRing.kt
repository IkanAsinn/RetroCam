package com.retrocam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.camera.ExposureInfo
import com.retrocam.logic.ExposureLogic
import com.retrocam.logic.FocusRingLogic
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal const val FOCUS_RING_RADIUS_DP = 36f
internal const val SLIDER_LENGTH_DP = 120f
internal const val SLIDER_THICKNESS_DP = 40f
internal const val LOCK_BUTTON_DP = 40f
private const val SUN_SIZE_DP = 28f
private const val LABEL_WIDTH_DP = 52f

/**
 * iOS-style vertical exposure slider that sits next to the focus ring: a thin track with a sun as the thumb. The box is
 * always upright in its own frame and the whole thing turns with [rotation], so "up" on the track is the user's up.
 * Touch input arrives in the turned frame, so no extra maths is needed. [side] is +1 when the slider sits to the right
 * of the ring (the value label then goes to its right, away from the ring) and -1 when it sits to the left. Touching the track jumps the sun to the
 * finger; dragging moves it, snapped to the camera's steps. [onInteraction] keeps the ring's fade timer alive.
 */
@Composable
fun FocusExposureSlider(
    info: ExposureInfo,
    index: Int,
    side: Int,
    labelVisible: Boolean,
    onIndexChange: (Int) -> Unit,
    onInteraction: () -> Unit,
    rotation: () -> Float,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val infoState = rememberUpdatedState(info)
    val indexState = rememberUpdatedState(index)
    val change = rememberUpdatedState(onIndexChange)
    val touch = rememberUpdatedState(onInteraction)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        while (dragging) {
            touch.value()
            delay(1_000)
        }
    }
    val valueText = ExposureLogic.format(info, index)
    val sliderDescription = stringResource(R.string.cd_exposure_slider)
    val sliderState = stringResource(R.string.exposure_state, valueText)
    val fraction = FocusRingLogic.fractionForIndex(index, info)
    val labelColor = if (index == 0) RetroColors.Cream else RetroColors.Amber

    Box(
        modifier = modifier
            .size(SLIDER_THICKNESS_DP.dp, SLIDER_LENGTH_DP.dp)
            .graphicsLayer {
                rotationZ = rotation()
                this.alpha = alpha()
            }
            .testTag("focus_slider")
            .semantics {
                contentDescription = sliderDescription
                stateDescription = sliderState
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = index.toFloat(),
                    range = info.minIndex.toFloat()..info.maxIndex.toFloat(),
                    steps = ExposureLogic.sliderSteps(info),
                )
                setProgress { target ->
                    change.value(ExposureLogic.snapIndex(target, infoState.value))
                    touch.value()
                    true
                }
            }
            .pointerInput(Unit) {
                val pad = (SUN_SIZE_DP / 2f).dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    fun apply(y: Float) {
                        val track = (size.height - 2f * pad).coerceAtLeast(1f)
                        val f = 1f - (y - pad) / track
                        val next = FocusRingLogic.indexForTrack(f, infoState.value)
                        if (next != indexState.value) change.value(next)
                        touch.value()
                    }
                    apply(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val c = event.changes.firstOrNull() ?: break
                        apply(c.position.y)
                        c.consume()
                        if (!c.pressed) break
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.size(SLIDER_THICKNESS_DP.dp, SLIDER_LENGTH_DP.dp)) {
            val pad = (SUN_SIZE_DP / 2f).dp.toPx()
            val x = size.width / 2f
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, pad), Offset(x, size.height - pad), strokeWidth = 4.dp.toPx())
            drawLine(RetroColors.Cream.copy(alpha = 0.85f), Offset(x, pad), Offset(x, size.height - pad), strokeWidth = 1.5.dp.toPx())
            // Tick at zero.
            val zeroY = pad + (1f - FocusRingLogic.fractionForIndex(0, info)) * (size.height - 2f * pad)
            drawLine(RetroColors.Cream, Offset(x - 5.dp.toPx(), zeroY), Offset(x + 5.dp.toPx(), zeroY), strokeWidth = 1.5.dp.toPx())
        }
        val sunY = with(androidx.compose.ui.platform.LocalDensity.current) {
            ((1f - fraction) * (SLIDER_LENGTH_DP.dp.toPx() - SUN_SIZE_DP.dp.toPx())).roundToInt()
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(0, sunY) }
                .size(SUN_SIZE_DP.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WbSunny,
                contentDescription = null,
                tint = RetroColors.Amber,
                modifier = Modifier.size(22.dp),
            )
        }
        if (labelVisible || dragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .requiredSize(LABEL_WIDTH_DP.dp, 24.dp)
                    .offset {
                        val x = if (side >= 0) (SLIDER_THICKNESS_DP + 4f).dp.roundToPx() else -(LABEL_WIDTH_DP + 4f).dp.roundToPx()
                        val sunCenter = sunY + (SUN_SIZE_DP / 2f).dp.roundToPx()
                        IntOffset(x, sunCenter - 12.dp.roundToPx())
                    },
                contentAlignment = if (side >= 0) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    text = valueText,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * The AE/AF lock button attached to the ring. Tapping toggles the lock (the same lock as the viewfinder pill and the
 * exposure row). [enabled] false only dims it: the tap still goes through so the caller can explain why it is refused.
 */
@Composable
fun FocusLockButton(
    locked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    rotation: () -> Float,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (locked) R.string.cd_ae_lock_on else R.string.cd_ae_lock_off)
    val tint = when {
        locked -> RetroColors.Amber
        enabled -> RetroColors.Cream
        else -> RetroColors.Cream.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .size(LOCK_BUTTON_DP.dp)
            .graphicsLayer { this.alpha = alpha() }
            .background(Color.Black.copy(alpha = if (locked) 0.7f else 0.5f), CircleShape)
            .testTag("focus_lock_button")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).uiRotate(rotation),
        )
    }
}
