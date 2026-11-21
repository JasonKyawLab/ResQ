package com.resq.app.debug

import android.os.SystemClock
import com.resq.app.core.data.db.SosLogEntity

/**
 * Fake received SOS entries for testing the Scan screen on one phone: one in each stage, with
 * made-up coordinates (a well-known test location, not a real person). Debug builds only: this file
 * lives in the debug source set, so the release app does not contain it.
 */
object DemoEntries {

    /** Demo sender IDs sit in a fixed range, so "Remove demo entries" deletes exactly these. */
    const val ID_FROM = 0x0DE00000
    const val ID_TO = 0x0DE0FFFF

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    fun build(nowEpochMillis: Long, nowElapsedMillis: Long = SystemClock.elapsedRealtime()): List<SosLogEntity> {
        fun entry(
            n: Int,
            message: String,
            ageMillis: Long,
            rssi: Int,
            hop: Int = 0,
            cancelled: Boolean = false,
            hasFullMessage: Boolean = true,
            positionUnknown: Boolean = false,
        ) = SosLogEntity(
            senderId = ID_FROM + n,
            message = message,
            latitude = if (positionUnknown) 0.0 else 37.42200 + n * 0.00031,
            longitude = if (positionUnknown) 0.0 else -122.08400 - n * 0.00027,
            batteryPercent = if (hasFullMessage) 40 else -1,
            rssi = rssi,
            hopCount = hop,
            firstSeenAtEpochMillis = nowEpochMillis - ageMillis - 10 * MINUTE,
            lastSeenAtEpochMillis = nowEpochMillis - ageMillis,
            hasFullMessage = hasFullMessage,
            updateCounter = 5,
            isRelayed = hop > 0,
            isCancelled = cancelled,
            positionUnknown = positionUnknown,
            positionTimeSeconds = if (positionUnknown) null else (nowEpochMillis - ageMillis) / 1000,
            sosStartedSeconds = (nowEpochMillis - ageMillis) / 1000 - 1800,
            sendIntervalMinutes = 5,
            lastSeenElapsedMillis = (nowElapsedMillis - ageMillis).coerceAtLeast(1L),
            dismissed = false,
        )

        return listOf(
            entry(1, "Third floor, room 304. Trapped, door blocked. 2 people.", 1 * MINUTE, rssi = -52),
            entry(2, "Need medical help. Leg injured, cannot walk.", 6 * MINUTE, rssi = -78, hop = 2),
            entry(3, "", 3 * MINUTE, rssi = -66, hasFullMessage = false),
            entry(4, "Under the stairs, ground floor. Phone battery low.", 3 * HOUR, rssi = -85),
            entry(5, "Got out. Safe now.", 20 * MINUTE, rssi = -70, cancelled = true),
            entry(6, "Old entry from two days ago.", 50 * HOUR, rssi = -90),
            entry(7, "Cannot get GPS. Basement, left side of the building.", 2 * MINUTE, rssi = -60, positionUnknown = true),
        )
    }
}
