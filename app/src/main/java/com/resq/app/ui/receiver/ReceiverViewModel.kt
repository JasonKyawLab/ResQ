package com.resq.app.ui.receiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.ble.BleReadiness
import com.resq.app.core.data.db.SosLogEntity
import com.resq.app.core.data.model.SignalStrength
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.ReadinessRefreshTrigger
import com.resq.app.core.service.ResQForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)

    val uiState: StateFlow<ReceiverUiState> = combine(
        locator.sessionController.state,
        locator.sosLogRepository.observeLog(),
        ReadinessRefreshTrigger.flow(application, locator),
    ) { session, entries, _ ->
        ReceiverUiState(
            isListening = session.isReceiving,
            entries = entries.map(::toUiItem),
            bluetoothReady = BleCapability.readinessToScan(application) == BleReadiness.Ready,
            permissionsGranted = PermissionManager.hasEverythingRequired(application),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverUiState())

    fun onToggleListening() {
        val context = getApplication<Application>()
        if (uiState.value.isListening) {
            ResQForegroundService.stopReceiving(context)
        } else {
            if (BleCapability.readinessToScan(context) != BleReadiness.Ready) return
            if (!PermissionManager.hasEverythingRequired(context)) return
            ResQForegroundService.startReceiving(context)
        }
    }

    private fun toUiItem(entity: SosLogEntity): SosLogItemUi = SosLogItemUi(
        senderId = entity.senderId,
        displayId = "#" + Integer.toHexString(entity.senderId).uppercase().takeLast(6),
        message = entity.message.ifBlank { "(location only — no message received yet)" },
        coordinateText = String.format(Locale.US, "%.5f, %.5f", entity.latitude, entity.longitude),
        signalStrength = SignalStrength.fromRssi(entity.rssi),
        lastSeenText = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(entity.lastSeenAtEpochMillis)),
        hopCount = entity.hopCount,
        isRelayed = entity.hopCount > 0,
    )

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReceiverViewModel(application) as T
            }
    }
}
