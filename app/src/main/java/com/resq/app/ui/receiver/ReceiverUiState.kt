package com.resq.app.ui.receiver

import com.resq.app.core.data.model.SignalStrength
import com.resq.app.core.data.repository.EntryStage

data class SosLogItemUi(
    val senderId: Int,
    val displayId: String,
    val message: String,
    val coordinateText: String,
    /** False when the sender had no GPS fix: there is nothing to show or copy. */
    val positionKnown: Boolean,
    val signalStrength: SignalStrength,
    /** "Heard 3 h ago (20:59)": the age first, a clock time only for older entries. */
    val heardText: String,
    /** "Position 4 min old", or null when the sender did not say when the position was taken. */
    val positionText: String?,
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
