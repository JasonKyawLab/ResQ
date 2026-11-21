package com.resq.app.ui.sender

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resq.app.core.ble.MAX_MESSAGE_BYTES
import com.resq.app.core.ble.truncateUtf8
import com.resq.app.core.data.model.SosMessage
import com.resq.app.ui.components.ActionBanner
import com.resq.app.ui.components.AppMark
import com.resq.app.ui.components.BeaconToggleButton
import com.resq.app.ui.components.rememberDraftField
import com.resq.app.ui.components.StatusCard
import com.resq.app.ui.components.StatusRow
import com.resq.app.ui.theme.BeaconAmber
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.Divider
import com.resq.app.ui.theme.OledBlack
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SurfaceCard
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SenderRoute(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SenderViewModel = viewModel(
        factory = SenderViewModel.factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    SenderScreen(
        state = uiState,
        onMessageChanged = viewModel::onMessageChanged,
        onRequestPermissions = onRequestPermissions,
        onRequestBluetoothEnable = onRequestBluetoothEnable,
        onToggleBroadcast = {
            if (!uiState.permissionsGranted) {
                onRequestPermissions()
            } else if (!uiState.bluetoothReady) {
                onRequestBluetoothEnable()
            } else {
                viewModel.onToggleBroadcast()
            }
        },
        modifier = modifier,
    )
}

/** Tap-to-add phrases, for someone who is hurt, scared, or not writing in their first language. */
private val QUICK_PHRASES = listOf(
    "Trapped",
    "Injured",
    "Need medical help",
    "Need water",
    "Lost",
    "Child with me",
    "Elderly person with me",
)

@Composable
private fun SenderScreen(
    state: SenderUiState,
    onMessageChanged: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onToggleBroadcast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The text lives in local state, not straight from the persisted draft.
    // The draft round-trips through DataStore -> Flow -> UiState, which lags a
    // few frames behind typing and makes the cursor jump and characters drop.
    val message = rememberDraftField(state.message)
    val focusManager = LocalFocusManager.current
    var messageFocused by remember { mutableStateOf(false) }

    // The saved location line (set in Settings) shares the message's byte budget.
    // If it grew since the message was written, trim the message so nothing is
    // silently cut off at broadcast time.
    val messageBudget = SosMessage.messageBudgetBytes(state.locationNote)
    LaunchedEffect(messageBudget) {
        val current = message.value.text
        val trimmed = current.truncateUtf8(messageBudget)
        if (trimmed != current) {
            message.value = TextFieldValue(trimmed, TextRange(trimmed.length))
            onMessageChanged(trimmed)
        }
    }

    // imePadding on the outer column lifts everything above the keyboard: the
    // scrolling content shrinks to fit, and the quick-phrase strip below it
    // stays pinned directly on top of the keyboard instead of hiding behind it.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Tap anywhere outside the field to put the keyboard away.
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppMark()
                Text(
                    text = "ResQ",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
            }

            when {
                !state.permissionsGranted -> ActionBanner(
                    message = "Bluetooth & location permissions are needed to broadcast.",
                    actionLabel = "Grant",
                    onAction = onRequestPermissions,
                )
                !state.bluetoothReady -> ActionBanner(
                    message = "Bluetooth is off — turn it on to broadcast.",
                    actionLabel = "Turn on",
                    onAction = onRequestBluetoothEnable,
                )
            }

            if (state.locationUnreliable) {
                StatusCard(
                    rows = listOf(
                        StatusRow(
                            "Location tip",
                            "GPS isn't available right now — describe where you are in the message below.",
                            BeaconAmber,
                        )
                    )
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = message.value,
                    onValueChange = { new ->
                        // Never accept more than can actually be broadcast.
                        val capped = new.text.truncateUtf8(messageBudget)
                        message.value = if (capped.length == new.text.length) {
                            new
                        } else {
                            new.copy(text = capped, selection = TextRange(capped.length))
                        }
                        onMessageChanged(capped)
                    },
                    label = { Text("SOS message") },
                    placeholder = { Text("Describe your situation…") },
                    supportingText = {
                        val used = SosMessage.compose(state.locationNote, message.value.text)
                            .toByteArray(Charsets.UTF_8).size
                        Text(
                            text = "$used / $MAX_MESSAGE_BYTES",
                            color = if (used >= MAX_MESSAGE_BYTES) BeaconAmber else TextSecondary,
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { messageFocused = it.isFocused },
                )

                if (state.locationNote.isNotBlank()) {
                    Text(
                        text = "Sent with your location: ${state.locationNote.trim()} (change in Settings)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            BeaconToggleButton(
                isActive = state.isBroadcasting,
                onToggle = onToggleBroadcast,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            val isAcquiringFirstFix = state.isBroadcasting && state.lastFix == null

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        isAcquiringFirstFix && state.positionUnknown -> "Broadcasting — no GPS signal yet"
                        isAcquiringFirstFix -> "Broadcasting — acquiring GPS…"
                        state.isBroadcasting -> "Broadcasting SOS"
                        else -> "Idle — tap to broadcast"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isBroadcasting) BeaconGreen else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isAcquiringFirstFix) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = BeaconGreen,
                    )
                }
            }

            StatusCard(
                rows = listOf(
                    StatusRow(
                        "Status",
                        if (state.isBroadcasting) "Broadcasting" else "Idle",
                        if (state.isBroadcasting) BeaconGreen else TextSecondary,
                    ),
                    StatusRow("Frequency", formatFrequency(state)),
                    StatusRow(
                        "Last coordinate",
                        formatCoordinate(state),
                        if (isAcquiringFirstFix) BeaconAmber else null,
                    ),
                    StatusRow(
                        "Last sent",
                        formatTimestamp(state),
                        if (isAcquiringFirstFix) BeaconAmber else null,
                    ),
                    StatusRow("Battery", formatBattery(state.batteryPercent)),
                ),
            )
        }

        // Quick phrases only while typing, so the screen stays clean otherwise.
        if (messageFocused) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_PHRASES.forEach { phrase ->
                    SuggestionChip(
                        onClick = {
                            val current = message.value.text.trimEnd()
                            val joined = if (current.isEmpty()) phrase else "$current. $phrase"
                            // Add the whole phrase or nothing: a half-cut phrase is worse than none.
                            if (joined.toByteArray(Charsets.UTF_8).size <= messageBudget) {
                                message.value = TextFieldValue(joined, TextRange(joined.length))
                                onMessageChanged(joined)
                            }
                        },
                        label = { Text(phrase) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary,
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = Divider,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatFrequency(state: SenderUiState): String =
    if (state.batterySaverEnabled) "${state.effectiveInterval.label} (battery saver)" else state.effectiveInterval.label

private fun formatCoordinate(state: SenderUiState): String {
    val fix = state.lastFix
    if (fix != null) return String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude)
    if (state.isBroadcasting && state.positionUnknown) return "No GPS yet"
    return if (state.isBroadcasting) "Acquiring…" else "—"
}

private fun formatTimestamp(state: SenderUiState): String {
    val epochMillis = state.lastBroadcastAtEpochMillis
    if (epochMillis != null) return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
    return if (state.isBroadcasting) "Acquiring…" else "—"
}

private fun formatBattery(percent: Int): String = if (percent < 0) "—" else "$percent%"
