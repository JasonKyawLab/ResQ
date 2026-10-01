package com.resq.app.ui.settings

import com.resq.app.core.data.model.BroadcastInterval

data class SettingsUiState(
    val senderIdDisplay: String = "—",
    val sendInterval: BroadcastInterval = BroadcastInterval.DEFAULT,
    val batterySaverEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
)
