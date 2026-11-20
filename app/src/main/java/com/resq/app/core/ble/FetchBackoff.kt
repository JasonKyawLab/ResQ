package com.resq.app.core.ble

/**
 * Back-off after a failed connection to a sender (for example Android's status 133).
 * Each failure makes the wait before the next try longer, so a phone does not hammer a
 * sender that is out of reach or busy. A success, or a newer update from the sender, resets it.
 * Time is passed in so the rules can be tested without a clock.
 */
class FetchBackoff(private val delaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS) {

    private class State(var failures: Int, var lastFailureAt: Long)

    private val states = HashMap<Int, State>()

    @Synchronized
    fun canAttempt(key: Int, nowMillis: Long): Boolean {
        val state = states[key] ?: return true
        return nowMillis - state.lastFailureAt >= waitAfter(state.failures)
    }

    @Synchronized
    fun recordFailure(key: Int, nowMillis: Long) {
        val state = states.getOrPut(key) { State(0, nowMillis) }
        state.failures++
        state.lastFailureAt = nowMillis
    }

    @Synchronized
    fun reset(key: Int) {
        states.remove(key)
    }

    /** The wait after [failures] failures in a row; the last step repeats. */
    fun waitAfter(failures: Int): Long =
        if (failures <= 0) 0L else delaysMillis[minOf(failures, delaysMillis.size) - 1]

    companion object {
        val DEFAULT_DELAYS_MILLIS = listOf(5_000L, 15_000L, 45_000L, 120_000L)
    }
}
