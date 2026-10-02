package com.resq.app.ui.sender

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.ble.BleReadiness
import com.resq.app.core.data.model.BroadcastInterval
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.location.LocationCapability
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.service.ResQForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SenderViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)

    private val readinessRefresh = MutableStateFlow(0)

    val uiState: StateFlow<SenderUiState> = combine(
        locator.sessionController.state,
        locator.settingsRepository.settings,
        readinessRefresh,
    ) { session, settings, _ ->
        SenderUiState(
            isBroadcasting = session.isSending,
            message = settings.sosMessageDraft,
            lastFix = session.lastFix,
            lastBroadcastAtEpochMillis = session.lastBroadcastAtEpochMillis,
            batteryPercent = if (session.batteryPercent >= 0) session.batteryPercent else locator.batteryPercent(),
            bluetoothReady = BleCapability.readinessToAdvertise(application) == BleReadiness.Ready,
            permissionsGranted = PermissionManager.hasEverythingRequired(application),
            locationUnreliable = LocationCapability.isUnreliable(application),
            effectiveInterval = if (settings.batterySaverEnabled) BroadcastInterval.BATTERY_SAVER else settings.sendInterval,
            batterySaverEnabled = settings.batterySaverEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SenderUiState())

    fun refreshReadiness() {
        readinessRefresh.value++
    }

    fun onMessageChanged(message: String) {
        viewModelScope.launch {
            locator.settingsRepository.setSosMessageDraft(message)
        }
    }

    fun onToggleBroadcast() {
        val context = getApplication<Application>()
        if (uiState.value.isBroadcasting) {
            ResQForegroundService.stopSending(context)
        } else {
            if (BleCapability.readinessToAdvertise(context) != BleReadiness.Ready) return
            if (!PermissionManager.hasEverythingRequired(context)) return
            ResQForegroundService.startSending(context)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SenderViewModel(application) as T
            }
    }
}
