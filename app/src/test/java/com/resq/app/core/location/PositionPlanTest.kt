package com.resq.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionPlanTest {

    @Test fun firstCycleWithNoFixBroadcastsPositionUnknown() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = false, lastMessage = null, message = "help"))

    @Test fun repeatedRetriesDoNotSendAgain() =
        assertFalse(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = true, lastMessage = "help", message = "help"))

    @Test fun aChangedMessageIsSentAgain() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = true, lastMessage = "help", message = "help, water rising"))

    @Test fun afterAFixWasSentUnknownIsSentOnlyIfPositionWasLost() =
        assertTrue(PositionPlan.shouldBroadcastWithoutFix(lastWasUnknown = false, lastMessage = "help", message = "help"))

    @Test fun positionTimeIsSecondsOfTheFixTime() =
        assertEquals(1_800_000_000L, PositionPlan.positionTimeSeconds(1_800_000_000_999L))

    @Test fun aFixWithoutARealTimeIsUnknownNotDecadesOld() {
        assertNull(PositionPlan.positionTimeSeconds(0L))
        assertNull(PositionPlan.positionTimeSeconds(-5L))
    }
}
