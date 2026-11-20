package com.resq.app.core.ble

/** What a phone does when it hears a beacon for a sender it may already know. */
enum class UpdateVerdict {
    /** A sender we have not seen, or a newer update: save it, fetch the full record, relay it later. */
    SAVE_AND_FETCH,

    /** The update we already hold: no fetch, no relay change. Only refresh "heard" and keep the lowest hop. */
    REFRESH_ONLY,

    /** An older update than the one we hold: ignore it completely. */
    IGNORE,
}

/**
 * The "once only" rules from the ROADMAP, as plain Kotlin so they run in unit
 * tests without a phone. The update counter is 16 bits and wraps from 65535
 * back to 0, so "newer" is decided by the shortest way round the circle
 * (the same idea as TCP sequence numbers), not by a plain greater-than.
 */
object UpdateRules {

    private const val COUNTER_SPACE = MAX_UPDATE_COUNTER + 1
    private const val HALF = COUNTER_SPACE / 2

    /** > 0 when [incoming] is newer than [stored], 0 when equal, < 0 when older. */
    fun compare(incoming: Int, stored: Int): Int {
        val diff = (incoming - stored) and MAX_UPDATE_COUNTER
        return when {
            diff == 0 -> 0
            diff < HALF -> 1
            else -> -1
        }
    }

    /** [storedCounter] is null when this sender is not known yet. */
    fun decide(storedCounter: Int?, incomingCounter: Int): UpdateVerdict = when {
        storedCounter == null -> UpdateVerdict.SAVE_AND_FETCH
        else -> when (compare(incomingCounter, storedCounter)) {
            1 -> UpdateVerdict.SAVE_AND_FETCH
            0 -> UpdateVerdict.REFRESH_ONLY
            else -> UpdateVerdict.IGNORE
        }
    }

    /**
     * Hop count to keep for the same update heard again: the lowest ever heard, so two
     * nearby relays passing it back and forth cannot inflate it. Hop is only a safety fuse.
     */
    fun lowestHop(stored: Int, incoming: Int): Int = minOf(stored, incoming)
}
