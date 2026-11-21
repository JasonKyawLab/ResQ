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
     */
    fun shouldBroadcastWithoutFix(lastWasUnknown: Boolean, lastMessage: String?, message: String): Boolean =
        !lastWasUnknown || lastMessage != message

    /** The time to send as the position time. A fix with no real time (0 or negative) is sent as unknown. */
    fun positionTimeSeconds(capturedAtEpochMillis: Long): Long? =
        if (capturedAtEpochMillis <= 0L) null else capturedAtEpochMillis / 1000
}
