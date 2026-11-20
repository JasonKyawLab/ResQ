package com.resq.app.core.data.repository

/** Where a received SOS is in its life on this phone. The order is the sort order on the Scan screen. */
enum class EntryStage {
    /** Heard recently: the sender is probably still sending. */
    LIVE,

    /** Not heard for a while. The last known position is still valuable; the sender may have lost power. */
    SIGNAL_LOST,

    /** The sender pressed Stop. Unverified: anyone nearby could have sent it. */
    MARKED_SAFE,

    /** Old. Kept a little longer, then removed. */
    ARCHIVED,
}

/**
 * The rules for how long a received SOS stays and what it is called, as plain Kotlin so they run
 * in unit tests. A dead phone's last known position is the most useful thing a rescuer can have,
 * so nothing is deleted after an hour: entries go Live, then Signal lost, then Archived, and are
 * removed only after [DELETE_AFTER_MILLIS].
 */
object EntryLifecycle {

    /** Used when the sender's interval is not known (an older build, or a relay without the extras). */
    const val DEFAULT_INTERVAL_MINUTES = 15

    const val MIN_LIVE_WINDOW_MILLIS = 10 * 60_000L
    const val MAX_LIVE_WINDOW_MILLIS = 2 * 60 * 60_000L
    const val ARCHIVE_AFTER_MILLIS = 48 * 3_600_000L
    const val DELETE_AFTER_MILLIS = 72 * 3_600_000L

    /** A "marked safe" entry is passed on for this long, so the news travels, and then not again. */
    const val CANCEL_RELAY_MILLIS = 15 * 60_000L

    /**
     * How long without hearing an entry before it is "Signal lost": about twice the sender's interval,
     * but never under 10 minutes (a relayed entry is heard rarely) or over 2 hours.
     */
    fun liveWindowMillis(sendIntervalMinutes: Int?): Long {
        val minutes = sendIntervalMinutes?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_MINUTES
        return (2 * minutes * 60_000L).coerceIn(MIN_LIVE_WINDOW_MILLIS, MAX_LIVE_WINDOW_MILLIS)
    }

    fun stage(isCancelled: Boolean, ageMillis: Long, sendIntervalMinutes: Int?): EntryStage = when {
        ageMillis >= ARCHIVE_AFTER_MILLIS -> EntryStage.ARCHIVED
        isCancelled -> EntryStage.MARKED_SAFE
        ageMillis <= liveWindowMillis(sendIntervalMinutes) -> EntryStage.LIVE
        else -> EntryStage.SIGNAL_LOST
    }

    /**
     * Whether this phone should pass the entry on. Only live ones: relaying an old position makes it look
     * current on other phones. A fresh "marked safe" is relayed briefly. A dismissed entry never is.
     */
    fun shouldRelay(stage: EntryStage, ageMillis: Long, dismissed: Boolean): Boolean = when {
        dismissed -> false
        stage == EntryStage.LIVE -> true
        stage == EntryStage.MARKED_SAFE -> ageMillis <= CANCEL_RELAY_MILLIS
        else -> false
    }

    /** "just now", "12 min", "3 h", "2 days": short and easy to read in a hurry. */
    fun ageText(ageMillis: Long): String {
        val minutes = ageMillis.coerceAtLeast(0L) / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min"
            minutes < 48 * 60 -> "${minutes / 60} h"
            else -> "${minutes / (24 * 60)} days"
        }
    }
}
