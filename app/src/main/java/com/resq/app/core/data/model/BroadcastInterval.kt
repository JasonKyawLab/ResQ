package com.resq.app.core.data.model

import androidx.annotation.StringRes
import com.resq.app.R

/**
 * How often the sender re-fetches a GPS fix and re-advertises it.
 *
 * This is the single dial that trades battery life for how stale a
 * rescuer's last-known fix can be, so every screen that touches
 * frequency (Sender status, Settings, Battery saver) reads from here
 * rather than storing its own interval constant.
 */
enum class BroadcastInterval(val minutes: Long, @StringRes val labelRes: Int) {
    EVERY_1_MIN(1, R.string.interval_1_min),
    EVERY_5_MIN(5, R.string.interval_5_min),
    EVERY_15_MIN(15, R.string.interval_15_min),
    HOURLY(60, R.string.interval_hourly);

    val millis: Long get() = minutes * 60_000L

    companion object {
        val DEFAULT = EVERY_5_MIN

        /** What battery saver forces. Not offered as a choice of its own: switching battery saver on is how to get it. */
        val BATTERY_SAVER = HOURLY

        /** The intervals the user can pick in Settings. */
        val SELECTABLE = listOf(EVERY_1_MIN, EVERY_5_MIN, EVERY_15_MIN)

        /** A saved choice that is no longer offered (such as an older "Hourly") falls back to the default. */
        fun fromMinutes(minutes: Long): BroadcastInterval =
            SELECTABLE.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
