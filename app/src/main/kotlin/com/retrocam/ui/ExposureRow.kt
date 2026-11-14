package com.retrocam.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.camera.ExposureInfo
import com.retrocam.logic.ExposureLogic
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.delay

/**
 * "[lock AE lock]  EV [-----o--]  +0.7" overlay row. The slider snaps to the camera's exposure steps; double-tapping
 * the value resets to zero (also offered as a TalkBack custom action). The lock toggle holds auto exposure and
 * white balance ([aeLockEnabled] false shows it dimmed; tapping still calls [onToggleLock] so the caller can explain why).
 * [onInteraction] fires on every change and about once a second while the thumb is held, to keep the auto-hide timer alive.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExposureRow(
    info: ExposureInfo,
    index: Int,
    onIndexChange: (Int) -> Unit,
    aeLocked: Boolean,
    aeLockEnabled: Boolean,
    onToggleLock: () -> Unit,
    onInteraction: () -> Unit,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val touch by rememberUpdatedState(onInteraction)
    LaunchedEffect(dragging) {
        while (dragging) {
            touch()
            delay(1_000)
        }
    }
    val haptic = LocalHapticFeedback.current
    val valueText = ExposureLogic.format(info, index)
    val resetLabel = stringResource(R.string.cd_exposure_reset)
    val reset = {
        if (index != 0) onIndexChange(0)
        onInteraction()
    }
    val valueDescription = stringResource(R.string.cd_exposure_value, valueText)
    val sliderDescription = stringResource(R.string.cd_exposure_slider)
    val sliderState = stringResource(R.string.exposure_state, valueText)
    val lockDescription = stringResource(if (aeLocked) R.string.cd_ae_lock_on else R.string.cd_ae_lock_off)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp)
            .testTag("exposure_row")
            .semantics { customActions = listOf(CustomAccessibilityAction(resetLabel) { reset(); true }) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .toggleable(value = aeLocked, role = Role.Switch) { onToggleLock(); onInteraction() }
                .semantics { contentDescription = lockDescription }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val tint = when {
                aeLocked -> RetroColors.Amber
                aeLockEnabled -> RetroColors.Cream
                else -> RetroColors.Cream.copy(alpha = 0.4f)
            }
            Icon(
                imageVector = if (aeLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.uiRotate(rotation),
            )
            Text(
                text = stringResource(R.string.ae_lock_label),
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.uiRotate(rotation),
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val snapped = ExposureLogic.snapIndex(it, info)
                if (snapped != index) {
                    onIndexChange(snapped)
                    if (snapped == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onInteraction()
            },
            valueRange = info.minIndex.toFloat()..info.maxIndex.toFloat(),
            steps = ExposureLogic.sliderSteps(info),
            interactionSource = interaction,
            colors = SliderDefaults.colors(
                thumbColor = RetroColors.Orange,
                activeTrackColor = RetroColors.Orange,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("exposure_slider")
                .semantics {
                    contentDescription = sliderDescription
                    stateDescription = sliderState
                },
        )
        Text(
            text = valueText,
            color = if (index == 0) RetroColors.Cream else RetroColors.Amber,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .heightIn(min = 44.dp)
                .combinedClickable(onClickLabel = resetLabel, onClick = {}, onDoubleClick = reset)
                .padding(top = 12.dp)
                .uiRotate(rotation)
                .semantics { contentDescription = valueDescription },
        )
    }
}
