package com.resq.app.ui.sender

import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.location.GpsFix

data class SenderUiState(
    val isBroadcasting: Boolean = false,
    val message: String = "",
    val lastFix: GpsFix? = null,
    val lastBroadcastAtEpochMillis: Long? = null,
    val batteryPercent: Int = -1,
    val bluetoothReady: Boolean = true,
    val permissionsGranted: Boolean = true,
    val locationUnreliable: Boolean = false,
    val effectiveInterval: BroadcastInterval = BroadcastInterval.DEFAULT,
    val batterySaverEnabled: Boolean = false,
)
