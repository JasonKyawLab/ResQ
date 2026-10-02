package com.resq.app.ui.settings

import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.permissions.PermissionStatus

data class SettingsUiState(
    val senderIdDisplay: String = "—",
    val sendInterval: BroadcastInterval = BroadcastInterval.DEFAULT,
    val batterySaverEnabled: Boolean = false,
    val bluetoothAdapterStatus: PermissionStatus = PermissionStatus.DENIED,
    val bluetoothPermissionStatus: PermissionStatus = PermissionStatus.DENIED,
    val locationPermissionStatus: PermissionStatus = PermissionStatus.DENIED,
    val locationServiceStatus: PermissionStatus = PermissionStatus.DENIED,
    val notificationStatus: PermissionStatus = PermissionStatus.GRANTED,
) {
    /** Drives the "describe your location in the message" tip — see LocationCapability's doc. */
    val locationUnreliable: Boolean
        get() = locationPermissionStatus != PermissionStatus.GRANTED ||
            locationServiceStatus != PermissionStatus.GRANTED
}
