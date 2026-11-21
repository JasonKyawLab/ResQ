package com.resq.app.core.service

import com.resq.app.core.location.GpsFix

/** Live snapshot of what the radio is doing right now, shared by the service and every screen. */
data class SessionState(
    val isSending: Boolean = false,
    val isReceiving: Boolean = false,
    val lastFix: GpsFix? = null,
    val lastBroadcastAtEpochMillis: Long? = null,
    val batteryPercent: Int = -1,
    /** The last broadcast went out without a position (no GPS fix yet). */
    val positionUnknown: Boolean = false,
)
