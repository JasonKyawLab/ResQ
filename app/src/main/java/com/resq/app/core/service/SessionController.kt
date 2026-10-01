package com.resq.app.core.service

import com.resq.app.core.location.GpsFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory source of truth for "is the radio on, and what did it last
 * do" — written by [ResQForegroundService] as it runs its loops, read by
 * every screen's ViewModel. Everything runs in a single process, so a
 * shared [StateFlow] is sufficient; there's no IPC/Messenger boundary to
 * cross here, which keeps the service-to-UI link trivial to reason about.
 */
class SessionController {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun setSending(active: Boolean) = _state.update { it.copy(isSending = active) }

    fun setReceiving(active: Boolean) = _state.update { it.copy(isReceiving = active) }

    fun recordFix(fix: GpsFix) = _state.update {
        it.copy(lastFix = fix, lastBroadcastAtEpochMillis = System.currentTimeMillis())
    }

    fun recordBatteryPercent(percent: Int) = _state.update { it.copy(batteryPercent = percent) }
}
