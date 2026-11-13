package com.retrocam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
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
import com.retrocam.profiles.layers
import com.retrocam.profiles.parseHexArgb
import com.retrocam.ui.theme.RetroColors
import kotlin.math.abs

private val CardWidth = 92.dp
private val CardHeight = 88.dp

/**
 * Horizontal strip of camera cards; the selected one is centred and outlined in orange.
 *
 * Selection rules (they exist to make a feedback loop impossible):
 *  - [onSelect] is only ever called for a user-initiated scroll (a drag/fling that has settled) or a tap.
 *  - Moving the strip because [selectedIndex] changed (tap, viewfinder swipe, restore) never calls [onSelect].
 *  - "Centre" is measured in item-offset coordinates: LazyListItemInfo.offset is relative to the content start, so with
 *    contentPadding the viewport centre is viewportStartOffset + viewportSize / 2, not viewportSize / 2.
 * [selectedStrengthPercent] below 100 shows a small badge on the selected card; tapping the selected card calls [onSelectedTap].
 */
@Composable
fun CameraStrip(
    profiles: List<CameraProfile>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
    selectedStrengthPercent: Int = 100,
    onSelectedTap: () -> Unit = {},
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val currentIndex = rememberUpdatedState(selectedIndex)
    val currentSelect = rememberUpdatedState(onSelect)

    // Programmatic selection (tap, viewfinder swipe) -> centre that card. Never reports back through onSelect.
    LaunchedEffect(selectedIndex) { listState.centreItem(selectedIndex) }

    // Remember that the user touched the list; only such a scroll may change the selection when it settles.
    val userScrolled = remember { booleanArrayOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) userScrolled[0] = true }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            if (!userScrolled[0]) return@collect
            userScrolled[0] = false
            val nearest = listState.itemNearestCentre()
            if (nearest != null && nearest != currentIndex.value) currentSelect.value(nearest)
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
                val selected = index == selectedIndex
                CameraCard(
                    profile, selected, rotation,
                    strengthPercent = if (selected) selectedStrengthPercent else 100,
                ) { if (selected) onSelectedTap() else onSelect(index) }
            }
        }
    }
}

/** Viewport centre in the same coordinates as [androidx.compose.foundation.lazy.LazyListItemInfo.offset]. */
internal fun LazyListLayoutInfo.viewportCentre(): Float = viewportStartOffset + (viewportEndOffset - viewportStartOffset) / 2f

internal fun LazyListState.itemNearestCentre(): Int? {
    val info = layoutInfo
    val centre = info.viewportCentre()
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - centre) }?.index
}

/** Scrolls so [index] sits exactly in the middle; animateScrollToItem lands on the content padding, then any residue is scrolled away. */
internal suspend fun LazyListState.centreItem(index: Int) {
    animateScrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = item.offset + item.size / 2f - info.viewportCentre()
    if (abs(delta) > 0.5f) animateScrollBy(delta)
}

@Composable
private fun CameraCard(
    profile: CameraProfile,
    selected: Boolean,
    rotation: () -> Float,
    strengthPercent: Int,
    onClick: () -> Unit,
) {
    val label = if (selected) stringResource(R.string.cd_camera_card_selected, profile.name)
    else stringResource(R.string.cd_camera_card, profile.name)
    val shape = RoundedCornerShape(14.dp)
    Box(contentAlignment = Alignment.TopEnd) {
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
    if (selected && strengthPercent != 100) {
        val badge = stringResource(R.string.strength_value, strengthPercent)
        val badgeDescription = stringResource(R.string.cd_strength_badge, strengthPercent)
        Text(
            text = badge,
            color = RetroColors.Charcoal,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(top = 4.dp, end = 4.dp)
                .background(RetroColors.Orange, RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
                .semantics { contentDescription = badgeDescription }
                .uiRotate(rotation),
        )
    }
    }
}

/** Body tinted with bodyColor, accent tinted with accentColor, detail untinted on top. */
@Composable
fun CardArtView(profile: CameraProfile, modifier: Modifier = Modifier) {
    val layers = remember(profile.card.body) { profile.card.body.layers() }
    val bodyColor = remember(profile.card.bodyColor) { Color(parseHexArgb(profile.card.bodyColor)) }
    val accentColor = remember(profile.card.accentColor) { Color(parseHexArgb(profile.card.accentColor)) }
    Box(modifier) {
        Image(painterResource(layers.body), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(bodyColor))
        Image(painterResource(layers.accent), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(accentColor))
        Image(painterResource(layers.detail), null, Modifier.fillMaxSize())
    }
}
