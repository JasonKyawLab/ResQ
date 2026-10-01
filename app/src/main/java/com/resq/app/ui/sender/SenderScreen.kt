package com.resq.app.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.ui.components.BeaconToggleButton
import com.resq.app.ui.components.StatusCard
import com.resq.app.ui.components.StatusRow
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SenderRoute(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SenderViewModel = viewModel(
        factory = SenderViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshReadiness() }

    SenderScreen(
        state = uiState,
        onMessageChanged = viewModel::onMessageChanged,
        onToggleBroadcast = {
            if (!uiState.permissionsGranted) {
                onRequestPermissions()
            } else {
                viewModel.onToggleBroadcast()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SenderScreen(
    state: SenderUiState,
    onMessageChanged: (String) -> Unit,
    onToggleBroadcast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "ResQ",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!state.permissionsGranted || !state.bluetoothReady) {
            ReadinessBanner(state)
        }

        OutlinedTextField(
            value = state.message,
            onValueChange = onMessageChanged,
            label = { Text("SOS message") },
            placeholder = { Text("Describe your situation…") },
            minLines = 3,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = SignalRed,
                cursorColor = SignalRed,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        BeaconToggleButton(
            isActive = state.isBroadcasting,
            onToggle = onToggleBroadcast,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        Text(
            text = if (state.isBroadcasting) "Broadcasting SOS" else "Idle — tap to broadcast",
            style = MaterialTheme.typography.titleMedium,
            color = if (state.isBroadcasting) BeaconGreen else TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )

        StatusCard(
            rows = listOf(
                StatusRow(
                    "Status",
                    if (state.isBroadcasting) "Broadcasting" else "Idle",
                    if (state.isBroadcasting) BeaconGreen else TextSecondary,
                ),
                StatusRow("Last coordinate", formatCoordinate(state)),
                StatusRow("Last sent", formatTimestamp(state.lastBroadcastAtEpochMillis)),
                StatusRow("Battery", formatBattery(state.batteryPercent)),
            ),
        )
    }
}

@Composable
private fun ReadinessBanner(state: SenderUiState) {
    val message = when {
        !state.permissionsGranted -> "Bluetooth & location permissions are needed to broadcast."
        !state.bluetoothReady -> "Turn on Bluetooth to broadcast."
        else -> null
    } ?: return

    StatusCard(rows = listOf(StatusRow("Action needed", message, SignalRed)))
}

private fun formatCoordinate(state: SenderUiState): String {
    val fix = state.lastFix ?: return "—"
    return String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude)
}

private fun formatTimestamp(epochMillis: Long?): String {
    if (epochMillis == null) return "—"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
}

private fun formatBattery(percent: Int): String = if (percent < 0) "—" else "$percent%"
