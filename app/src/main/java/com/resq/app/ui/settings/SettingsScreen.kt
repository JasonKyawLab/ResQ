package com.resq.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.core.data.model.BroadcastInterval
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshReadiness() }

    SettingsScreen(
        state = uiState,
        onIntervalSelected = viewModel::onIntervalSelected,
        onBatterySaverToggled = viewModel::onBatterySaverToggled,
        onRequestBluetoothEnable = onRequestBluetoothEnable,
        onRequestRuntimePermissions = onRequestRuntimePermissions,
        onOpenAppSettings = onOpenAppSettings,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onIntervalSelected: (BroadcastInterval) -> Unit,
    onBatterySaverToggled: (Boolean) -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

        SettingsSection(title = "Send frequency") {
            Text(
                "How often a fresh GPS fix is fetched and re-broadcast. Longer intervals save battery.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BroadcastInterval.entries.forEach { interval ->
                    IntervalRow(
                        interval = interval,
                        selected = state.sendInterval == interval && !state.batterySaverEnabled,
                        enabled = !state.batterySaverEnabled,
                        onClick = { onIntervalSelected(interval) },
                    )
                }
            }
        }

        SettingsSection(title = "Battery saver") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Minimal UI, longest interval", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(
                        "Forces hourly broadcasts regardless of the setting above.",
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

        SettingsSection(title = "Permissions") {
            PermissionRow("Bluetooth adapter", state.bluetoothEnabled, onRequestBluetoothEnable, "Turn on")
            PermissionRow(
                "Bluetooth permission",
                state.bluetoothPermissionGranted,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Location permission",
                state.locationPermissionGranted,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Notifications",
                state.notificationPermissionGranted,
                onRequestRuntimePermissions,
                "Grant",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Manage everything from the system settings page if a toggle doesn't respond.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.clickable(onClick = onOpenAppSettings),
            )
        }

        SettingsSection(title = "Device") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sender ID", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(state.senderIdDisplay, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
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
            interval.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) TextPrimary else TextSecondary,
        )
        if (selected) {
            Text("✓", color = BeaconGreen, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onAction: () -> Unit, actionLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (granted) "●" else "○",
                color = if (granted) BeaconGreen else SignalRed,
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        if (!granted) {
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
