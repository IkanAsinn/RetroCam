package com.retrocam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.profiles.CameraProfile
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

private val CardWidth = 92.dp
private val CardHeight = 88.dp

/** Horizontal strip of camera cards; the selected one is centred and outlined in orange. */
@Composable
fun CameraStrip(
    profiles: List<CameraProfile>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val currentIndex = rememberUpdatedState(selectedIndex)
    val currentSelect = rememberUpdatedState(onSelect)

    // Programmatic selection (tap or swipe on the viewfinder) -> centre that card.
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }

    // User fling settled -> the card nearest the centre becomes the selection.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).collect { scrolling ->
            if (scrolling) return@collect
            val info = listState.layoutInfo
            val centre = info.viewportSize.width / 2f
            val nearest = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - centre) }
            if (nearest != null && nearest.index != currentIndex.value) currentSelect.value(nearest.index)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(CardHeight + 12.dp)) {
        val sidePadding = ((maxWidth - CardWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(profiles, key = { _, p -> p.id }) { index, profile ->
                CameraCard(profile, index == selectedIndex, rotation) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun CameraCard(profile: CameraProfile, selected: Boolean, rotation: () -> Float, onClick: () -> Unit) {
    val label = if (selected) stringResource(R.string.cd_camera_card_selected, profile.name)
    else stringResource(R.string.cd_camera_card, profile.name)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(CardWidth)
            .height(CardHeight)
            .clip(shape)
            .border(
                border = if (selected) BorderStroke(2.dp, RetroColors.Orange) else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected; role = Role.Button }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CardArtView(profile, Modifier.size(width = 72.dp, height = 54.dp))
        Text(
            text = profile.name,
            color = if (selected) RetroColors.Cream else RetroColors.Cream.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).uiRotate(rotation),
        )
    }
}

/** Body tinted with bodyColor, accent tinted with accentColor, detail untinted on top. */
@Composable
fun CardArtView(profile: CameraProfile, modifier: Modifier = Modifier) {
    val layers = remember(profile.card.body) { profile.card.body.artLayers() }
    val bodyColor = remember(profile.card.bodyColor) { parseColor(profile.card.bodyColor, Color(0xFFBBBBBB)) }
    val accentColor = remember(profile.card.accentColor) { parseColor(profile.card.accentColor, Color(0xFF808080)) }
    Box(modifier) {
        Image(painterResource(layers.body), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(bodyColor))
        if (layers.accent != 0) {
            Image(painterResource(layers.accent), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(accentColor))
        }
        if (layers.detail != 0) {
            Image(painterResource(layers.detail), null, Modifier.fillMaxSize())
        }
    }
}

private fun parseColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    fallback
}
