package com.retrocam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrocam.CameraScreenState
import com.retrocam.R
import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.logic.FlashLogic
import com.retrocam.data.TimerSetting
import com.retrocam.profiles.FlareMode
import com.retrocam.profiles.GrainLevel
import com.retrocam.profiles.LeakMode
import com.retrocam.ui.theme.RetroColors

class TopBarActions(
    val onFlash: () -> Unit,
    val onFilmFlash: () -> Unit,
    val onGrain: () -> Unit,
    val onFlare: () -> Unit,
    val onLeaks: () -> Unit,
    val onDate: () -> Unit,
    val onTimer: () -> Unit,
    val onStrength: () -> Unit,
    val onSettings: () -> Unit,
)

/** Flash, film flash, grain, flare, light leak, date stamp, timer: each cycles on tap, long-press shows its label. */
@Composable
fun TopBar(
    state: CameraScreenState,
    rotation: () -> Float,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
    strengthOpen: Boolean = false,
) {
    val s = state.settings
    val facing = state.camera?.facing ?: Facing.BACK
    val hasFlash = state.camera?.let { FlashLogic.available(it.facing, it.hasFlashUnit) } == true
    val flashMode = FlashLogic.displayMode(s.flash, facing, state.camera?.hasFlashUnit ?: true)
    val off = stringResource(R.string.state_off)

    val flashState = when (flashMode) {
        FlashMode.OFF -> off
        FlashMode.AUTO -> stringResource(R.string.state_auto)
        FlashMode.ON -> stringResource(R.string.state_on)
    }
    val grainState = when (s.grain) {
        GrainLevel.OFF -> off
        GrainLevel.LOW -> stringResource(R.string.state_low)
        GrainLevel.NORMAL -> stringResource(R.string.state_normal)
        GrainLevel.HIGH -> stringResource(R.string.state_high)
    }
    val flareState = when (s.flare) {
        FlareMode.OFF -> off
        FlareMode.AUTO -> stringResource(R.string.state_auto)
        FlareMode.ALWAYS -> stringResource(R.string.state_always)
    }
    val leakState = when (s.leaks) {
        LeakMode.OFF -> off
        LeakMode.SUBTLE -> stringResource(R.string.state_subtle)
        LeakMode.STRONG -> stringResource(R.string.state_strong)
    }
    val dateState = if (s.dateStamp) stringResource(R.string.state_on) else off
    val timerState = when (s.timer) {
        TimerSetting.OFF -> off
        TimerSetting.THREE -> stringResource(R.string.state_timer_3)
        TimerSetting.TEN -> stringResource(R.string.state_timer_10)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 9 equal slots must stay at least a usable touch width; narrower screens scroll instead of dropping toggles.
        val compact = maxWidth / TOGGLE_COUNT < MIN_SLOT_WIDTH
        val rowModifier = Modifier.fillMaxWidth().let { if (compact) it.horizontalScroll(rememberScrollState()) else it }
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        TopToggle(
            icon = when (flashMode) {
                FlashMode.OFF -> Icons.Outlined.FlashOff
                FlashMode.AUTO -> Icons.Outlined.FlashAuto
                FlashMode.ON -> Icons.Outlined.FlashOn
            },
            description = if (hasFlash) stringResource(R.string.desc_flash, flashState) else stringResource(R.string.desc_flash_unavailable),
            badge = when (flashMode) {
                FlashMode.OFF -> null
                FlashMode.AUTO -> stringResource(R.string.badge_auto)
                FlashMode.ON -> stringResource(R.string.badge_on)
            },
            active = hasFlash && flashMode != FlashMode.OFF,
            enabled = hasFlash,
            rotation = rotation,
            compact = compact,
            onClick = actions.onFlash,
        )
        TopToggle(
            icon = Icons.Outlined.AutoAwesome,
            description = stringResource(R.string.desc_film_flash),
            badge = if (s.filmFlash) stringResource(R.string.badge_on) else null,
            active = s.filmFlash,
            rotation = rotation,
            compact = compact,
            onClick = actions.onFilmFlash,
        )
        TopToggle(
            icon = Icons.Outlined.Grain,
            description = stringResource(R.string.desc_grain, grainState),
            badge = when (s.grain) {
                GrainLevel.OFF -> null
                GrainLevel.LOW -> stringResource(R.string.badge_low)
                GrainLevel.NORMAL -> stringResource(R.string.badge_normal)
                GrainLevel.HIGH -> stringResource(R.string.badge_high)
            },
            active = s.grain != GrainLevel.OFF,
            rotation = rotation,
            compact = compact,
            onClick = actions.onGrain,
        )
        TopToggle(
            icon = Icons.Outlined.Flare,
            description = stringResource(R.string.desc_flare, flareState),
            badge = when (s.flare) {
                FlareMode.OFF -> null
                FlareMode.AUTO -> stringResource(R.string.badge_auto)
                FlareMode.ALWAYS -> stringResource(R.string.badge_always)
            },
            active = s.flare != FlareMode.OFF,
            rotation = rotation,
            compact = compact,
            onClick = actions.onFlare,
        )
        TopToggle(
            icon = Icons.Outlined.Gradient,
            description = stringResource(R.string.desc_leaks, leakState),
            badge = when (s.leaks) {
                LeakMode.OFF -> null
                LeakMode.SUBTLE -> stringResource(R.string.badge_subtle)
                LeakMode.STRONG -> stringResource(R.string.badge_strong)
            },
            active = s.leaks != LeakMode.OFF,
            rotation = rotation,
            compact = compact,
            onClick = actions.onLeaks,
        )
        TopToggle(
            icon = Icons.Outlined.CalendarToday,
            description = stringResource(R.string.desc_date, dateState),
            badge = if (s.dateStamp) stringResource(R.string.badge_on) else null,
            active = s.dateStamp,
            rotation = rotation,
            compact = compact,
            onClick = actions.onDate,
        )
        TopToggle(
            icon = Icons.Outlined.Timer,
            description = stringResource(R.string.desc_timer, timerState),
            badge = when (s.timer) {
                TimerSetting.OFF -> null
                TimerSetting.THREE -> stringResource(R.string.badge_timer_3)
                TimerSetting.TEN -> stringResource(R.string.badge_timer_10)
            },
            active = s.timer != TimerSetting.OFF,
            rotation = rotation,
            compact = compact,
            onClick = actions.onTimer,
        )
        val strength = s.strengthFor(s.cameraId)
        TopToggle(
            icon = Icons.Outlined.Tune,
            description = stringResource(R.string.desc_strength, stringResource(R.string.strength_value, strength)),
            badge = if (strength != 100) stringResource(R.string.strength_value, strength) else null,
            active = strength != 100 || strengthOpen,
            rotation = rotation,
            compact = compact,
            onClick = actions.onStrength,
        )
        TopToggle(
            icon = Icons.Outlined.Settings,
            description = stringResource(R.string.desc_settings),
            badge = null,
            active = false,
            rotation = rotation,
            compact = compact,
            onClick = actions.onSettings,
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.TopToggle(
    icon: ImageVector,
    description: String,
    badge: String?,
    active: Boolean,
    rotation: () -> Float,
    compact: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = if (active) RetroColors.Orange else RetroColors.Cream
    // The Row's direct child must carry the weight: TooltipBox does not forward its modifier's parent data.
    Box(
        modifier = if (compact) Modifier.width(MIN_SLOT_WIDTH) else Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(description) } },
            state = rememberTooltipState(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp) // touch target height; width is the slot width
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = description; role = Role.Button }
                    .alpha(if (enabled) 1f else 0.35f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.uiRotate(rotation),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        text = badge ?: "",
                        color = tint,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private const val TOGGLE_COUNT = 9
private val MIN_SLOT_WIDTH = 40.dp // 9 x 40dp = 360dp: every toggle fits a 360dp phone; narrower screens scroll
