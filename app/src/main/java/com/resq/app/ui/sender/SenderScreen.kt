package com.resq.app.ui.sender

import com.resq.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
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
import com.resq.app.ui.components.ScreenHeader
import com.resq.app.ui.components.ScreenHorizontalPadding
import com.resq.app.ui.components.ScreenVerticalPadding
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
                .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenHeader(title = stringResource(R.string.app_name), leading = { AppMark() })

            when {
                !state.permissionsGranted -> ActionBanner(
                    message = stringResource(R.string.send_banner_permissions),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = onRequestPermissions,
                )
                !state.bluetoothReady -> ActionBanner(
                    message = stringResource(R.string.send_banner_bluetooth_off),
                    actionLabel = stringResource(R.string.action_turn_on),
                    onAction = onRequestBluetoothEnable,
                )
            }

            val isAcquiringFirstFix = state.isBroadcasting && state.lastFix == null

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
                    label = { Text(stringResource(R.string.send_message_label)) },
                    placeholder = { Text(stringResource(R.string.send_message_placeholder)) },
                    supportingText = {
                        val used = SosMessage.compose(state.locationNote, message.value.text)
                            .toByteArray(Charsets.UTF_8).size
                        Text(
                            text = stringResource(R.string.char_count, used, MAX_MESSAGE_BYTES),
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
                        text = stringResource(R.string.send_location_note_line, state.locationNote.trim()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            // Shown right after Start with an empty message: one tap adds what you need. The SOS is
            // already on the air without it, and the edit goes out a moment after the tap.
            if (state.isBroadcasting && message.value.text.isBlank()) {
                PresetCards(
                    onFill = { phrase ->
                        message.value = TextFieldValue(phrase, TextRange(phrase.length))
                        onMessageChanged(phrase)
                    },
                )
            }

            BeaconToggleButton(
                isActive = state.isBroadcasting,
                onToggle = onToggleBroadcast,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (state.locationUnreliable) {
                Text(
                    text = stringResource(R.string.send_no_gps_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeaconAmber,
                    textAlign = TextAlign.Center,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        isAcquiringFirstFix && state.positionUnknown -> stringResource(R.string.send_status_no_gps)
                        isAcquiringFirstFix -> stringResource(R.string.send_status_acquiring)
                        state.isBroadcasting -> stringResource(R.string.send_status_broadcasting)
                        else -> stringResource(R.string.send_status_idle)
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
                        stringResource(R.string.send_row_status),
                        stringResource(if (state.isBroadcasting) R.string.send_row_status_broadcasting else R.string.send_row_status_idle),
                        if (state.isBroadcasting) BeaconGreen else TextSecondary,
                    ),
                    StatusRow(stringResource(R.string.send_row_frequency), formatFrequency(state)),
                    StatusRow(
                        stringResource(R.string.send_row_last_coordinate),
                        formatCoordinate(state),
                        if (isAcquiringFirstFix) BeaconAmber else null,
                    ),
                    StatusRow(
                        stringResource(R.string.send_row_last_sent),
                        formatTimestamp(state),
                        if (isAcquiringFirstFix) BeaconAmber else null,
                    ),
                    StatusRow(stringResource(R.string.send_row_battery), formatBattery(state.batteryPercent)),
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
                SOS_PHRASES.forEach { phraseId ->
                    val phrase = stringResource(phraseId)
                    val resources = LocalContext.current.resources
                    SuggestionChip(
                        onClick = {
                            val current = message.value.text.trimEnd()
                            val joined = if (current.isEmpty()) phrase else resources.getString(R.string.send_phrase_joiner, current, phrase)
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

@Composable
private fun formatFrequency(state: SenderUiState): String {
    val label = stringResource(state.effectiveInterval.labelRes)
    return if (state.batterySaverEnabled) stringResource(R.string.send_frequency_battery_saver, label) else label
}

@Composable
private fun formatCoordinate(state: SenderUiState): String {
    val fix = state.lastFix
    if (fix != null) return String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude)
    if (state.isBroadcasting && state.positionUnknown) return stringResource(R.string.send_no_gps_yet)
    return stringResource(if (state.isBroadcasting) R.string.acquiring else R.string.dash)
}

@Composable
private fun formatTimestamp(state: SenderUiState): String {
    val epochMillis = state.lastBroadcastAtEpochMillis
    if (epochMillis != null) return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
    return stringResource(if (state.isBroadcasting) R.string.acquiring else R.string.dash)
}

@Composable
private fun formatBattery(percent: Int): String = if (percent < 0) stringResource(R.string.dash) else "$percent%"

@Composable
private fun PresetCards(onFill: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.send_presets_prompt),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        SOS_PHRASES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { phraseId ->
                    val phrase = stringResource(phraseId)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .background(SurfaceCard, RoundedCornerShape(14.dp))
                            .border(1.5.dp, SignalRed, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onFill(phrase) }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = phrase,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
