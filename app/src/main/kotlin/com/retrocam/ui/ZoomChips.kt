package com.retrocam.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.camera.ZoomStop
import com.retrocam.filmengine.FrameFormat
import com.retrocam.filmengine.PhotoRatio
import com.retrocam.ui.theme.RetroColors

/** What the ratio chip shows: [label] ("4:3", "1:1", "Instax"...) and whether the camera's fixed format makes it read-only. */
data class RatioChip(val label: String, val locked: Boolean)

/** What the EV chip shows: [value] is the formatted compensation ("±0", "+0.7"); [highlighted] when non-zero, locked or open. */
data class ExposureChip(val value: String, val highlighted: Boolean)

@StringRes
fun PhotoRatio.labelRes(): Int = when (this) {
    PhotoRatio.R4_3 -> R.string.ratio_4_3
    PhotoRatio.R3_2 -> R.string.ratio_3_2
    PhotoRatio.R16_9 -> R.string.ratio_16_9
    PhotoRatio.R1_1 -> R.string.ratio_1_1
}

/** Label of the dimmed ratio chip for a fixed-format camera (a FrameSelection.choose result other than FULL). */
@StringRes
fun FrameFormat.fixedLabelRes(): Int = when (this) {
    FrameFormat.SQUARE -> R.string.ratio_1_1
    FrameFormat.INSTANT_SQUARE, FrameFormat.INSTANT_MINI -> R.string.ratio_instax
    FrameFormat.HALF_FRAME -> R.string.ratio_half
    FrameFormat.FULL -> R.string.ratio_4_3
}

/**
 * Photo-ratio chip followed by the lens/zoom chips above the shutter. The lens chips are hidden (row height kept)
 * when the camera reports fewer than two stops; the ratio chip always shows. Tapping the chip calls [onRatio]
 * (the caller cycles the ratio or explains that the format is fixed).
 */
@Composable
fun ZoomChips(
    stops: List<ZoomStop>,
    selectedIndex: Int,
    rotation: () -> Float,
    onSelect: (ZoomStop) -> Unit,
    modifier: Modifier = Modifier,
    ratio: RatioChip? = null,
    onRatio: () -> Unit = {},
    exposure: ExposureChip? = null,
    onExposure: () -> Unit = {},
) {
    // Centred when the chips fit; on narrow screens with many lenses the row scrolls instead of clipping chips.
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ratio != null) {
            val description = stringResource(if (ratio.locked) R.string.cd_ratio_fixed else R.string.cd_ratio, ratio.label)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRatio)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ratio.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = RetroColors.Cream.copy(alpha = if (ratio.locked) 0.45f else 1f),
                    modifier = Modifier
                        .uiRotate(rotation)
                        .background(Color.White.copy(alpha = if (ratio.locked) 0.06f else 0.12f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (exposure != null || stops.size >= 2) Spacer(Modifier.width(4.dp))
        }
        if (exposure != null) {
            val description = stringResource(R.string.cd_exposure_chip, exposure.value)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onExposure)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.exposure_chip, exposure.value),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (exposure.highlighted) RetroColors.Amber else RetroColors.Cream,
                    modifier = Modifier
                        .uiRotate(rotation)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (stops.size >= 2) Spacer(Modifier.width(4.dp))
        }
        if (stops.size < 2) return@Row
        stops.forEachIndexed { index, stop ->
            val selected = index == selectedIndex
            val description = stringResource(if (selected) R.string.cd_zoom_selected else R.string.cd_zoom, stop.label)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onSelect(stop) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stop.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) RetroColors.Charcoal else RetroColors.Cream,
                    modifier = Modifier
                        .uiRotate(rotation)
                        .background(if (selected) RetroColors.Amber else Color.White.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    }
}
