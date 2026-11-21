package com.resq.app.ui.receiver

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.ui.components.ActionBanner
import com.resq.app.ui.components.SignalBadge
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.SignalRed
import com.resq.app.core.data.repository.EntryStage
import com.resq.app.ui.theme.BeaconAmber
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
        onDismiss = viewModel::onDismiss,
        debugActions = viewModel.debugActions,
        modifier = modifier,
    )
}

@Composable
private fun ReceiverScreen(
    state: ReceiverUiState,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onToggleListening: () -> Unit,
    onDismiss: (Int) -> Unit,
    debugActions: List<com.resq.app.ui.components.DebugAction>,
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

        com.resq.app.ui.components.DebugPanel(title = "Scan screen", actions = debugActions)

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
                    SosLogCard(entry, onDismiss = { onDismiss(entry.senderId) })
                }
            }
        }
    }
}

@Composable
private fun EmptyLogState(isListening: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = BeaconGreen)
                Text(
                    "No SOS signals detected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else {
            Text(
                "Start scan to listen for SOS broadcasts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SosLogCard(entry: SosLogItemUi, onDismiss: () -> Unit) {
    // A marked-safe or archived entry's text is dimmed so a live one stands out. The buttons are not:
    // Dismiss and Copy stay easy to see and tap on every card.
    val dimmed = entry.stage == EntryStage.MARKED_SAFE || entry.stage == EntryStage.ARCHIVED
    val contentAlpha = if (dimmed) 0.6f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().alpha(contentAlpha),
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

        Text(
            entry.statusText,
            modifier = Modifier.alpha(contentAlpha),
            style = MaterialTheme.typography.labelMedium,
            color = when (entry.stage) {
                EntryStage.LIVE -> BeaconGreen
                EntryStage.SIGNAL_LOST -> BeaconAmber
                else -> TextSecondary
            },
        )

        Text(
            entry.message,
            modifier = Modifier.alpha(contentAlpha),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )

        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stacked, not side by side: with a large system font the two no longer
            // fit on one line and the time used to wrap into the coordinates.
            Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
                Text(entry.coordinateText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                entry.positionText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Text(entry.heardText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Row {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
            if (entry.positionKnown) TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(entry.coordinateText))
                    // Android 13+ shows its own "copied" confirmation; older versions need one.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text("Copy", color = SignalRed, fontWeight = FontWeight.SemiBold)
            }
            }
        }
    }
}
