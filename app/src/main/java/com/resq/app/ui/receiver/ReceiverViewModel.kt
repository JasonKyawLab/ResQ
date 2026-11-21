package com.resq.app.ui.receiver

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.ble.BleReadiness
import com.resq.app.core.data.db.SosLogEntity
import com.resq.app.core.data.model.SignalStrength
import com.resq.app.core.data.repository.EntryLifecycle
import com.resq.app.core.data.repository.EntryStage
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.ReadinessRefreshTrigger
import com.resq.app.core.service.ResQForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)

    /** Entries change stage with time even when nothing is saved, so re-check every minute. */
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val uiState: StateFlow<ReceiverUiState> = combine(
        locator.sessionController.state,
        locator.sosLogRepository.observeLog(),
        ReadinessRefreshTrigger.flow(application, locator),
        minuteTick,
    ) { session, entries, _, _ ->
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        ReceiverUiState(
            isListening = session.isReceiving,
            entries = entries.map { toUiItem(it, now, nowElapsed) }
                .sortedWith(compareBy<SosLogItemUi> { it.stage }.thenByDescending { lastSeenOf(entries, it.senderId) }),
            bluetoothReady = BleCapability.readinessToScan(application) == BleReadiness.Ready,
            permissionsGranted = PermissionManager.hasEverythingRequired(application),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiverUiState())

    /** Testing buttons (debug builds only; the release build returns none). */
    val debugActions: List<com.resq.app.ui.components.DebugAction> =
        com.resq.app.debug.DemoTools.actions(locator)

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

    fun onDismiss(senderId: Int) {
        viewModelScope.launch { locator.sosLogRepository.dismiss(senderId) }
    }

    private fun lastSeenOf(entries: List<SosLogEntity>, senderId: Int): Long =
        entries.firstOrNull { it.senderId == senderId }?.lastSeenAtEpochMillis ?: 0L

    private fun toUiItem(entity: SosLogEntity, now: Long, nowElapsed: Long): SosLogItemUi {
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = entity.lastSeenAtEpochMillis,
            lastSeenElapsedMillis = entity.lastSeenElapsedMillis,
            nowWallMillis = now,
            nowElapsedMillis = nowElapsed,
        )
        val stage = EntryLifecycle.stage(entity.isCancelled, age, entity.sendIntervalMinutes)
        val status = when (stage) {
            EntryStage.LIVE -> "Live"
            EntryStage.SIGNAL_LOST -> "Signal lost · last known position, ${EntryLifecycle.ageText(age)} old"
            EntryStage.MARKED_SAFE -> "Marked safe by sender (unverified)"
            EntryStage.ARCHIVED -> "Archived · last heard ${EntryLifecycle.ageText(age)} ago"
        }
        return buildItem(entity, stage, status, age, now)
    }

    private fun buildItem(
        entity: SosLogEntity,
        stage: EntryStage,
        status: String,
        ageMillis: Long,
        now: Long,
    ): SosLogItemUi = SosLogItemUi(
        senderId = entity.senderId,
        displayId = "#" + Integer.toHexString(entity.senderId).uppercase().takeLast(6),
        message = entity.message.ifBlank { "(location only — no message received yet)" },
        coordinateText = if (entity.positionUnknown) {
            "Position unknown (sender has no GPS signal)"
        } else {
            String.format(Locale.US, "%.5f, %.5f", entity.latitude, entity.longitude)
        },
        positionKnown = !entity.positionUnknown,
        signalStrength = SignalStrength.fromRssi(entity.rssi),
        heardText = EntryLifecycle.heardLine(
            ageMillis = ageMillis,
            timeOfDay = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entity.lastSeenAtEpochMillis)),
            dateAndTime = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entity.lastSeenAtEpochMillis)),
        ),
        positionText = if (entity.positionUnknown) null else EntryLifecycle.positionLine(
            EntryLifecycle.positionAgeMillis(entity.positionTimeSeconds, now),
        ),
        hopCount = entity.hopCount,
        isRelayed = entity.hopCount > 0,
        stage = stage,
        statusText = status,
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
