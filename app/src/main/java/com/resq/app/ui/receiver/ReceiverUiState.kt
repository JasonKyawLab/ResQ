package com.resq.app.ui.receiver

import com.resq.app.core.data.model.SignalStrength
import com.resq.app.core.data.repository.EntryStage

data class SosLogItemUi(
    val senderId: Int,
    val displayId: String,
    val message: String,
    val coordinateText: String,
    val signalStrength: SignalStrength,
    val lastSeenText: String,
    val hopCount: Int,
    val isRelayed: Boolean,
    val stage: EntryStage,
    /** One line under the ID: Live / Signal lost / Marked safe (unverified) / Archived. */
    val statusText: String,
)

data class ReceiverUiState(
    val isListening: Boolean = false,
    val entries: List<SosLogItemUi> = emptyList(),
    val bluetoothReady: Boolean = true,
    val permissionsGranted: Boolean = true,
)
