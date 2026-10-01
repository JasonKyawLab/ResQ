package com.resq.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)
    private val readinessRefresh = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        locator.settingsRepository.settings,
        readinessRefresh,
    ) { settings, _ ->
        SettingsUiState(
            senderIdDisplay = "#" + Integer.toHexString(settings.senderId).uppercase().takeLast(6),
            sendInterval = settings.sendInterval,
            batterySaverEnabled = settings.batterySaverEnabled,
            bluetoothEnabled = BleCapability.isBluetoothEnabled(application),
            bluetoothPermissionGranted = PermissionManager.hasBluetoothPermissions(application),
            locationPermissionGranted = PermissionManager.hasLocationPermissions(application),
            notificationPermissionGranted = PermissionManager.hasNotificationPermission(application),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { locator.settingsRepository.senderIdOrCreate() }
    }

    fun refreshReadiness() {
        readinessRefresh.value++
    }

    fun onIntervalSelected(interval: BroadcastInterval) {
        viewModelScope.launch { locator.settingsRepository.setSendInterval(interval) }
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
