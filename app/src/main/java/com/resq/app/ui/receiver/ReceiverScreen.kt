package com.resq.app.ui.receiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.ui.components.ActionBanner
import com.resq.app.ui.components.SignalBadge
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SurfaceCard
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary

@Composable
fun ReceiverRoute(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: ReceiverViewModel = viewModel(
        factory = ReceiverViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    ReceiverScreen(
        state = uiState,
        onRequestPermissions = onRequestPermissions,
        onRequestBluetoothEnable = onRequestBluetoothEnable,
        onToggleListening = {
            if (!uiState.permissionsGranted) onRequestPermissions() else viewModel.onToggleListening()
        },
        modifier = modifier,
    )
}

@Composable
private fun ReceiverScreen(
    state: ReceiverUiState,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onToggleListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nearby SOS", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Button(
                onClick = onToggleListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isListening) SurfaceCard else SignalRed,
                    contentColor = TextPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (state.isListening) "Stop scan" else "Start scan")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (state.isListening) "Scanning • relaying what we hear" else "Scan is off",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isListening) BeaconGreen else TextSecondary,
        )

        when {
            !state.permissionsGranted -> {
                Spacer(Modifier.height(12.dp))
                ActionBanner(
                    message = "Bluetooth & location permissions are needed to scan.",
                    actionLabel = "Grant",
                    onAction = onRequestPermissions,
                )
            }
            !state.bluetoothReady -> {
                Spacer(Modifier.height(12.dp))
                ActionBanner(
                    message = "Bluetooth is off — turn it on to scan.",
                    actionLabel = "Turn on",
                    onAction = onRequestBluetoothEnable,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.entries.isEmpty()) {
            EmptyLogState(isListening = state.isListening)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.entries, key = { it.senderId }) { entry ->
                    SosLogCard(entry)
                }
            }
        }
    }
}

@Composable
private fun EmptyLogState(isListening: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (isListening) "No SOS signals detected yet." else "Start scan to listen for SOS broadcasts.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SosLogCard(entry: SosLogItemUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.displayId,
                    style = MaterialTheme.typography.titleMedium,
                    color = SignalRed,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.isRelayed) {
                    Text(
                        "relayed ×${entry.hopCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
            SignalBadge(entry.signalStrength)
        }

        Text(entry.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(entry.coordinateText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("seen ${entry.lastSeenText}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
