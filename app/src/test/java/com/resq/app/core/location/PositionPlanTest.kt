package com.resq.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionPlanTest {

    private val MIN5 = 5 * 60_000L

    @Test fun lostGpsRepeatsTheLastPositionOncePerInterval() {
        assertTrue(PositionPlan.shouldRepeatLastPosition(MIN5, MIN5))
        assertFalse(PositionPlan.shouldRepeatLastPosition(MIN5 - 1, MIN5))
    }

    @Test fun aWholeIntervalWithoutAFixSendsAHeartbeat() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(true, "help", "help", millisSinceLastBroadcast = MIN5, intervalMillis = MIN5))

    @Test fun justBeforeTheIntervalThereIsNoHeartbeat() =
        assertFalse(PositionPlan.shouldBroadcastWithoutFix(true, "help", "help", millisSinceLastBroadcast = MIN5 - 1, intervalMillis = MIN5))

    @Test fun firstCycleWithNoFixBroadcastsPositionUnknown() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = false, lastMessage = null, message = "help", millisSinceLastBroadcast = 0, intervalMillis = MIN5))

    @Test fun repeatedRetriesDoNotSendAgain() =
        assertFalse(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = true, lastMessage = "help", message = "help", millisSinceLastBroadcast = 30_000, intervalMillis = MIN5))

    @Test fun aChangedMessageIsSentAgain() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = true, lastMessage = "help", message = "help, water rising", millisSinceLastBroadcast = 30_000, intervalMillis = MIN5))

    @Test fun afterAFixWasSentUnknownIsSentOnlyIfPositionWasLost() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = false, lastMessage = "help", message = "help", millisSinceLastBroadcast = 30_000, intervalMillis = MIN5))

    @Test fun positionTimeIsSecondsOfTheFixTime() =
        assertEquals(1_800_000_000L, PositionPlan.positionTimeSeconds(1_800_000_000_999L))

    @Test fun aFixWithoutARealTimeIsUnknownNotDecadesOld() {
        assertNull(PositionPlan.positionTimeSeconds(0L))
        assertNull(PositionPlan.positionTimeSeconds(-5L))
    }
}
