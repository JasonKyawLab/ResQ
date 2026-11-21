package com.resq.app.core.location

/**
 * Small decisions about what to broadcast when GPS is slow or missing, as plain Kotlin so they run in
 * unit tests. The person who most needs rescue (under rubble, indoors) is often the one with no fix,
 * so "no fix" must not mean "send nothing".
 */
object PositionPlan {

    /**
     * On a cycle with no fix and no earlier position this session: whether to put a "position unknown"
     * broadcast on the air. Once is enough; repeating it every retry would bump the update counter and
     * make every helper reconnect for nothing. A changed message is worth sending again.
     *
     * Heartbeat: once a whole send interval has passed, send it again anyway, with a newer counter. A
     * sender that is reachable only through relays would otherwise look "Signal lost" while still alive,
     * because a repeat of the same update no longer proves it is.
     */
    fun shouldBroadcastWithoutFix(
        lastWasUnknown: Boolean,
        lastMessage: String?,
        message: String,
        millisSinceLastBroadcast: Long,
        intervalMillis: Long,
    ): Boolean = !lastWasUnknown || lastMessage != message || millisSinceLastBroadcast >= intervalMillis

    /**
     * No fix this cycle but an earlier position exists: whether to send that position again, with a newer
     * counter and its original position time, so the entry stays "Live" on phones that only hear it
     * through relays. Once per send interval, never on every retry.
     */
    fun shouldRepeatLastPosition(millisSinceLastBroadcast: Long, intervalMillis: Long): Boolean =
        millisSinceLastBroadcast >= intervalMillis

    /** The time to send as the position time. A fix with no real time (0 or negative) is sent as unknown. */
    fun positionTimeSeconds(capturedAtEpochMillis: Long): Long? =
        if (capturedAtEpochMillis <= 0L) null else capturedAtEpochMillis / 1000
}
