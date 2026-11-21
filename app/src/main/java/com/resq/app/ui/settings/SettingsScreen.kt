package com.resq.app.ui.settings

import com.resq.app.ui.theme.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.PaddingValues
import com.resq.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.core.ble.truncateUtf8
import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.data.model.SosMessage
import com.resq.app.core.permissions.PermissionStatus
import com.resq.app.ui.components.StatusCard
import com.resq.app.ui.components.TipCard
import com.resq.app.ui.components.StatusRow
import com.resq.app.ui.components.rememberDraftField
import com.resq.app.ui.components.DebugPanel
import com.resq.app.ui.theme.BeaconAmber
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SurfaceCard
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary

@Composable
fun SettingsRoute(
    onRequestBluetoothEnable: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreen(
        state = uiState,
        onLocationNoteChanged = viewModel::onLocationNoteChanged,
        onIntervalSelected = viewModel::onIntervalSelected,
        onBatterySaverToggled = viewModel::onBatterySaverToggled,
        onClearReceived = viewModel::onClearReceived,
        onRequestBluetoothEnable = onRequestBluetoothEnable,
        onRequestRuntimePermissions = onRequestRuntimePermissions,
        onOpenAppSettings = onOpenAppSettings,
        onOpenLocationSettings = onOpenLocationSettings,
        onOpenGuide = onOpenGuide,
        onOpenBatterySettings = onOpenBatterySettings,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onLocationNoteChanged: (String) -> Unit,
    onIntervalSelected: (BroadcastInterval) -> Unit,
    onBatterySaverToggled: (Boolean) -> Unit,
    onClearReceived: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locationNote = rememberDraftField(state.locationNote)
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

        if (state.locationUnreliable) {
            TipCard(stringResource(R.string.settings_location_tip_label), stringResource(R.string.settings_location_tip), BeaconAmber)
        }

        SettingsSection(title = stringResource(R.string.settings_my_location)) {
            Text(
                stringResource(R.string.settings_my_location_help),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = locationNote.value,
                onValueChange = { new ->
                    val capped = new.text.truncateUtf8(SosMessage.LOCATION_NOTE_MAX_BYTES)
                    locationNote.value = if (capped.length == new.text.length) {
                        new
                    } else {
                        new.copy(text = capped, selection = TextRange(capped.length))
                    }
                    onLocationNoteChanged(capped)
                },
                label = { Text(stringResource(R.string.settings_my_location_label)) },
                placeholder = { Text(stringResource(R.string.settings_my_location_placeholder)) },
                supportingText = {
                    val used = locationNote.value.text.toByteArray(Charsets.UTF_8).size
                    Text(stringResource(R.string.char_count, used, SosMessage.LOCATION_NOTE_MAX_BYTES), color = TextSecondary)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = SignalRed,
                    cursorColor = SignalRed,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_battery_saver)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_battery_saver_title), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(
                        stringResource(R.string.settings_battery_saver_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = state.batterySaverEnabled,
                    onCheckedChange = onBatterySaverToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = SignalRed),
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_frequency)) {
            Text(
                stringResource(R.string.settings_frequency_help),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(if (state.batterySaverEnabled) 0.35f else 1f),
            ) {
                BroadcastInterval.SELECTABLE.forEach { interval ->
                    IntervalRow(
                        interval = interval,
                        selected = state.sendInterval == interval && !state.batterySaverEnabled,
                        enabled = !state.batterySaverEnabled,
                        onClick = { onIntervalSelected(interval) },
                    )
                }
            }
            if (state.batterySaverEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_frequency_locked),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_permissions)) {
            PermissionRow(stringResource(R.string.perm_bluetooth_adapter), state.bluetoothAdapterStatus, onRequestBluetoothEnable, stringResource(R.string.action_turn_on))
            PermissionRow(
                stringResource(R.string.perm_bluetooth_scan),
                state.bluetoothScanStatus,
                onRequestRuntimePermissions,
                stringResource(R.string.action_grant),
            )
            PermissionRow(
                stringResource(R.string.perm_bluetooth_advertise),
                state.bluetoothAdvertiseStatus,
                onRequestRuntimePermissions,
                stringResource(R.string.action_grant),
            )
            PermissionRow(
                stringResource(R.string.perm_bluetooth_connect),
                state.bluetoothConnectStatus,
                onRequestRuntimePermissions,
                stringResource(R.string.action_grant),
            )
            PermissionRow(
                stringResource(R.string.perm_location),
                state.locationPermissionStatus,
                onRequestRuntimePermissions,
                stringResource(R.string.action_grant),
            )
            PermissionRow(
                stringResource(R.string.perm_location_services),
                state.locationServiceStatus,
                onOpenLocationSettings,
                stringResource(R.string.action_turn_on),
            )
            PermissionRow(
                stringResource(R.string.perm_notifications),
                state.notificationStatus,
                onRequestRuntimePermissions,
                stringResource(R.string.action_grant),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(BeaconGreen, stringResource(R.string.perm_set))
                LegendDot(SignalRed, stringResource(R.string.perm_not_set))
                LegendDot(BeaconAmber, stringResource(R.string.perm_unavailable))
            }
            HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Text(
                stringResource(R.string.perm_manage_system),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            TextButton(onClick = onOpenAppSettings, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.perm_open_system), color = SignalRed, fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSection(title = stringResource(R.string.settings_battery_optimisation)) {
            Text(
                stringResource(R.string.battery_help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            if (state.batteryOptimisationStatus == PermissionStatus.GRANTED) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "✓ " + stringResource(R.string.battery_exempt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeaconGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.battery_open)) }
        }

        SettingsSection(title = stringResource(R.string.settings_guide_title)) {
            Text(
                stringResource(R.string.settings_guide_help),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenGuide,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_guide_button)) }
        }

        var confirmClear by remember { mutableStateOf(false) }
        SettingsSection(title = stringResource(R.string.settings_received)) {
            Text(
                stringResource(R.string.settings_received_help),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmClear = true },
                border = BorderStroke(1.5.dp, SignalRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_clear_button)) }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.settings_clear_title)) },
                text = { Text(stringResource(R.string.settings_clear_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClearReceived() }) {
                        Text(stringResource(R.string.settings_clear_confirm), color = SignalRed)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.settings_cancel)) } },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_device)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_last_sos_id), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(state.senderIdDisplay, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
            DebugPanel(title = "Saved SOS", rows = state.debugRows)
        }

        Text(
            stringResource(R.string.settings_credit),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(16.dp))
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun IntervalRow(
    interval: BroadcastInterval,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(interval.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) TextPrimary else TextSecondary,
        )
        if (selected) {
            Text("✓", color = BeaconGreen, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    status: PermissionStatus,
    onAction: () -> Unit,
    actionLabel: String,
) {
    val (dotColor, statusText) = when (status) {
        PermissionStatus.GRANTED -> BeaconGreen to stringResource(R.string.perm_set)
        PermissionStatus.DENIED -> SignalRed to stringResource(R.string.perm_not_set)
        PermissionStatus.UNAVAILABLE -> BeaconAmber to stringResource(R.string.perm_unavailable)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("●", color = dotColor)
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                if (status == PermissionStatus.UNAVAILABLE) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
        if (status == PermissionStatus.DENIED) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = SignalRed, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("●", color = color, style = MaterialTheme.typography.labelSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
