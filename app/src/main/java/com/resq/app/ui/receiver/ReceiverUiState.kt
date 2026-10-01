package com.resq.app.ui.receiver

import com.resq.app.core.data.model.SignalStrength

data class SosLogItemUi(
    val senderId: Int,
    val displayId: String,
    val message: String,
    val coordinateText: String,
    val signalStrength: SignalStrength,
    val lastSeenText: String,
    val hopCount: Int,
    val isRelayed: Boolean,
)

data class ReceiverUiState(
    val isListening: Boolean = false,
    val entries: List<SosLogItemUi> = emptyList(),
    val bluetoothReady: Boolean = true,
    val permissionsGranted: Boolean = true,
)
