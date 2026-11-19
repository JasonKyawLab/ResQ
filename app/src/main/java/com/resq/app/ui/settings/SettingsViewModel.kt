package com.resq.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.location.LocationCapability
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.ReadinessRefreshTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)

    val uiState: StateFlow<SettingsUiState> = combine(
        locator.settingsRepository.settings,
        ReadinessRefreshTrigger.flow(application, locator),
    ) { settings, _ ->
        SettingsUiState(
            senderIdDisplay = if (settings.senderId == 0) "—" else "#" + Integer.toHexString(settings.senderId).uppercase().takeLast(6),
            locationNote = settings.locationNote,
            sendInterval = settings.sendInterval,
            batterySaverEnabled = settings.batterySaverEnabled,
            bluetoothAdapterStatus = BleCapability.adapterStatus(application),
            bluetoothScanStatus = BleCapability.scanPermissionStatus(application),
            bluetoothAdvertiseStatus = BleCapability.advertisePermissionStatus(application),
            bluetoothConnectStatus = BleCapability.connectPermissionStatus(application),
            locationPermissionStatus = LocationCapability.permissionStatus(application),
            locationServiceStatus = LocationCapability.serviceStatus(application),
            notificationStatus = PermissionManager.notificationStatus(application),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onIntervalSelected(interval: BroadcastInterval) {
        viewModelScope.launch { locator.settingsRepository.setSendInterval(interval) }
    }

    fun onLocationNoteChanged(note: String) {
        viewModelScope.launch { locator.settingsRepository.setLocationNote(note) }
    }

    fun onBatterySaverToggled(enabled: Boolean) {
        viewModelScope.launch { locator.settingsRepository.setBatterySaverEnabled(enabled) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(application) as T
            }
    }
}
