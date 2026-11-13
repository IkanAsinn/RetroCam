package com.retrocam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.retrocam.ui.theme.RetroColors

/** Lens/zoom chips above the shutter. Hidden (but space kept) when the camera reports fewer than two stops. */
@Composable
fun ZoomChips(
    stops: List<ZoomStop>,
    selectedIndex: Int,
    rotation: () -> Float,
    onSelect: (ZoomStop) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
