package com.retrocam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
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
import com.retrocam.camera.FlashMode
import com.retrocam.data.TimerSetting
import com.retrocam.profiles.FlareMode
import com.retrocam.profiles.GrainLevel
import com.retrocam.profiles.LeakMode
import com.retrocam.ui.theme.RetroColors

class TopBarActions(
    val onFlash: () -> Unit,
    val onGrain: () -> Unit,
    val onFlare: () -> Unit,
    val onLeaks: () -> Unit,
    val onDate: () -> Unit,
    val onTimer: () -> Unit,
    val onSettings: () -> Unit,
)

/** Flash, grain, flare, light leak, date stamp, timer: each cycles on tap, long-press shows its label. */
@Composable
fun TopBar(state: CameraScreenState, rotation: () -> Float, actions: TopBarActions, modifier: Modifier = Modifier) {
    val s = state.settings
    val hasFlash = state.camera?.hasFlashUnit == true
    val off = stringResource(R.string.state_off)

    val flashState = when (s.flash) {
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

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopToggle(
            icon = when (s.flash) {
                FlashMode.OFF -> Icons.Outlined.FlashOff
                FlashMode.AUTO -> Icons.Outlined.FlashAuto
                FlashMode.ON -> Icons.Outlined.FlashOn
            },
            description = if (hasFlash) stringResource(R.string.desc_flash, flashState) else stringResource(R.string.desc_flash_unavailable),
            badge = when (s.flash) {
                FlashMode.OFF -> null
                FlashMode.AUTO -> stringResource(R.string.badge_auto)
                FlashMode.ON -> stringResource(R.string.badge_on)
            },
            active = hasFlash && s.flash != FlashMode.OFF,
            enabled = hasFlash,
            rotation = rotation,
            onClick = actions.onFlash,
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
            onClick = actions.onLeaks,
        )
        TopToggle(
            icon = Icons.Outlined.CalendarToday,
            description = stringResource(R.string.desc_date, dateState),
            badge = if (s.dateStamp) stringResource(R.string.badge_on) else null,
            active = s.dateStamp,
            rotation = rotation,
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
            onClick = actions.onTimer,
        )
        TopToggle(
            icon = Icons.Outlined.Settings,
            description = stringResource(R.string.desc_settings),
            badge = null,
            active = false,
            rotation = rotation,
            onClick = actions.onSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopToggle(
    icon: ImageVector,
    description: String,
    badge: String?,
    active: Boolean,
    rotation: () -> Float,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = if (active) RetroColors.Orange else RetroColors.Cream
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp) // minimum touch target
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
