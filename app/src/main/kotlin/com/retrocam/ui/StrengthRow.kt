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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.logic.StrengthMath
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.delay

/**
 * Slim "Strength  [-----o--]  80%" row. Slider 0..100 in steps of 5; double-tapping the value resets to 100.
 * [onInteraction] is called on every change and about once a second while the thumb is held, so the
 * caller can keep the row open for its auto-hide timer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StrengthRow(
    percent: Int,
    onChange: (Int) -> Unit,
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
    val resetLabel = stringResource(R.string.cd_strength_reset)
    val reset = {
        onChange(StrengthMath.FULL)
        onInteraction()
    }
    val sliderDescription = stringResource(R.string.cd_strength_slider)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp)
            .semantics { customActions = listOf(CustomAccessibilityAction(resetLabel) { reset(); true }) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.strength_label),
            color = RetroColors.Cream,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.uiRotate(rotation),
        )
        Slider(
            value = percent.toFloat(),
            onValueChange = {
                val snapped = StrengthMath.snap(it)
                if (snapped != percent) onChange(snapped)
                onInteraction()
            },
            valueRange = 0f..100f,
            steps = 19, // 5% steps
            interactionSource = interaction,
            colors = SliderDefaults.colors(
                thumbColor = RetroColors.Orange,
                activeTrackColor = RetroColors.Orange,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).semantics { contentDescription = sliderDescription },
        )
        Text(
            text = stringResource(R.string.strength_value, percent),
            color = if (percent == StrengthMath.FULL) RetroColors.Cream else RetroColors.Amber,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .heightIn(min = 44.dp)
                .combinedClickable(onClickLabel = resetLabel, onClick = {}, onDoubleClick = reset)
                .padding(top = 12.dp)
                .uiRotate(rotation),
        )
    }
}
