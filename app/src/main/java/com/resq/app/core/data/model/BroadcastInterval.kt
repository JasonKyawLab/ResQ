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
        val BATTERY_SAVER = HOURLY

        fun fromMinutes(minutes: Long): BroadcastInterval =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
