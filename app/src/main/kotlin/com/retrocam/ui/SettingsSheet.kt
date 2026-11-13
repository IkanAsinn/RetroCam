package com.retrocam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.data.Settings
import com.retrocam.filmengine.DateStampFormat
import com.retrocam.ui.theme.RetroColors

class SettingsActions(
    val onShutterSound: (Boolean) -> Unit,
    val onDateFormat: (DateStampFormat) -> Unit,
    val onFrameOption: (Boolean) -> Unit,
    val onExifLocation: (Boolean) -> Unit,
    val onZoomUnlocked: (Boolean) -> Unit,
    val onSendCrashLog: () -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: Settings, versionName: String, actions: SettingsActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RetroColors.CharcoalRaised,
        contentColor = RetroColors.Cream,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))

            SwitchRow(R.string.settings_shutter_sound, R.string.settings_shutter_sound_desc, settings.shutterSound, actions.onShutterSound)

            Column(Modifier.padding(vertical = 12.dp)) {
                Text(stringResource(R.string.settings_date_format), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_date_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroColors.Cream.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val formats = listOf(
                    DateStampFormat.YY_MM_DD to R.string.format_yy_mm_dd,
                    DateStampFormat.DD_MM_YY to R.string.format_dd_mm_yy,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { i, (format, label) ->
                        SegmentedButton(
                            selected = settings.dateFormat == format,
                            onClick = { actions.onDateFormat(format) },
                            shape = SegmentedButtonDefaults.itemShape(i, formats.size),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            SwitchRow(R.string.settings_frame, R.string.settings_frame_desc, settings.frameOption, actions.onFrameOption)
            SwitchRow(R.string.settings_location, R.string.settings_location_desc, settings.exifLocation, actions.onExifLocation)
            SwitchRow(R.string.settings_zoom, R.string.settings_zoom_desc, settings.zoomUnlocked, actions.onZoomUnlocked)

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = RetroColors.Cream.copy(alpha = 0.15f))

            Text(stringResource(R.string.settings_crash_desc), style = MaterialTheme.typography.bodySmall, color = RetroColors.Cream.copy(alpha = 0.7f))
            OutlinedButton(
                onClick = actions.onSendCrashLog,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_crash)) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = RetroColors.Cream.copy(alpha = 0.15f))

            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.about_promise), style = MaterialTheme.typography.bodyMedium, color = RetroColors.Amber)
        }
    }
}

@Composable
private fun SwitchRow(title: Int, subtitle: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = RetroColors.Cream.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
