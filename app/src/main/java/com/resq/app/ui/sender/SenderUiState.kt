package com.resq.app.ui.sender

import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.location.GpsFix

data class SenderUiState(
    val isBroadcasting: Boolean = false,
    val message: String = "",
    val locationNote: String = "",
    val lastFix: GpsFix? = null,
    /** Broadcasting, but no GPS fix yet: the SOS goes out without a position. */
    val positionUnknown: Boolean = false,
    val lastBroadcastAtEpochMillis: Long? = null,
    val batteryPercent: Int = -1,
    val bluetoothReady: Boolean = true,
    val permissionsGranted: Boolean = true,
    val locationUnreliable: Boolean = false,
    val effectiveInterval: BroadcastInterval = BroadcastInterval.DEFAULT,
    val batterySaverEnabled: Boolean = false,
)
