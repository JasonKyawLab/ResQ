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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.permissions.PermissionStatus
import com.resq.app.ui.components.StatusCard
import com.resq.app.ui.components.StatusRow
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreen(
        state = uiState,
        onIntervalSelected = viewModel::onIntervalSelected,
        onBatterySaverToggled = viewModel::onBatterySaverToggled,
        onRequestBluetoothEnable = onRequestBluetoothEnable,
        onRequestRuntimePermissions = onRequestRuntimePermissions,
        onOpenAppSettings = onOpenAppSettings,
        onOpenLocationSettings = onOpenLocationSettings,
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
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

        if (state.locationUnreliable) {
            StatusCard(
                rows = listOf(
                    StatusRow(
                        "Location tip",
                        "GPS isn't available right now — you can still describe where you are in the SOS message text.",
                        BeaconAmber,
                    )
                )
            )
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
                        "Forces hourly broadcasts regardless of the setting below.",
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

        SettingsSection(title = "Send frequency") {
            Text(
                "How often a fresh GPS fix is fetched and re-broadcast. Longer intervals save battery.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(if (state.batterySaverEnabled) 0.35f else 1f),
            ) {
                BroadcastInterval.entries.forEach { interval ->
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
                    "Locked while battery saver is on — turn it off to pick a interval.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        SettingsSection(title = "Permissions") {
            PermissionRow("Bluetooth adapter", state.bluetoothAdapterStatus, onRequestBluetoothEnable, "Turn on")
            PermissionRow(
                "Bluetooth — scan",
                state.bluetoothScanStatus,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Bluetooth — advertise",
                state.bluetoothAdvertiseStatus,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Bluetooth — connect",
                state.bluetoothConnectStatus,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Location permission",
                state.locationPermissionStatus,
                onRequestRuntimePermissions,
                "Grant",
            )
            PermissionRow(
                "Location services",
                state.locationServiceStatus,
                onOpenLocationSettings,
                "Turn on",
            )
            PermissionRow(
                "Notifications",
                state.notificationStatus,
                onRequestRuntimePermissions,
                "Grant",
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(BeaconGreen, "Set")
                LegendDot(SignalRed, "Not set")
                LegendDot(BeaconAmber, "Unavailable")
            }
            Spacer(Modifier.height(10.dp))
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
private fun PermissionRow(
    label: String,
    status: PermissionStatus,
    onAction: () -> Unit,
    actionLabel: String,
) {
    val (dotColor, statusText) = when (status) {
        PermissionStatus.GRANTED -> BeaconGreen to "Set"
        PermissionStatus.DENIED -> SignalRed to "Not set"
        PermissionStatus.UNAVAILABLE -> BeaconAmber to "Unavailable"
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
