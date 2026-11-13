package com.retrocam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.logic.CameraPermissionUi
import com.retrocam.ui.theme.RetroColors

/** Friendly rationale on first launch; a settings shortcut once the system dialog can no longer be shown. */
@Composable
fun PermissionScreen(mode: CameraPermissionUi, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(RetroColors.Charcoal).safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = RetroColors.Amber, modifier = Modifier.size(72.dp))
        Text(
            stringResource(R.string.perm_title),
            style = MaterialTheme.typography.headlineSmall,
            color = RetroColors.Cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            stringResource(
                when (mode) {
                    CameraPermissionUi.OPEN_SETTINGS -> R.string.perm_denied_body
                    CameraPermissionUi.ASK_AGAIN -> R.string.perm_retry_body
                    else -> R.string.perm_body
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = RetroColors.Cream.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            onClick = if (mode == CameraPermissionUi.OPEN_SETTINGS) onOpenSettings else onAllow,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (mode == CameraPermissionUi.OPEN_SETTINGS) R.string.perm_open_settings else R.string.perm_allow))
        }
    }
}
