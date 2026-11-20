package com.resq.app.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLimiterTest {

    @Test fun admitsUpToTheCapThenRefuses() {
        val limiter = ConnectionLimiter(2)
        assertTrue(limiter.tryAdmit("a"))
        assertTrue(limiter.tryAdmit("b"))
        assertFalse(limiter.tryAdmit("c"))
        assertEquals(2, limiter.count())
    }

    @Test fun aFreedSlotCanBeUsedAgain() {
        val limiter = ConnectionLimiter(1)
        assertTrue(limiter.tryAdmit("a"))
        assertFalse(limiter.tryAdmit("b"))
        limiter.release("a")
        assertTrue(limiter.tryAdmit("b"))
    }

    @Test fun admittingTheSamePhoneTwiceIsNotTwoConnections() {
        val limiter = ConnectionLimiter(2)
        assertTrue(limiter.tryAdmit("a"))
        assertTrue(limiter.tryAdmit("a"))
        assertEquals(1, limiter.count())
    }

    @Test fun releasingARefusedPhoneDoesNotFreeAnotherSlot() {
        val limiter = ConnectionLimiter(1)
        limiter.tryAdmit("a")
        assertFalse(limiter.tryAdmit("b"))
        limiter.release("b") // b disconnects after being dropped
        assertFalse(limiter.tryAdmit("c"))
        assertEquals(1, limiter.count())
    }

    @Test fun clearEmptiesEverything() {
        val limiter = ConnectionLimiter(3)
        limiter.tryAdmit("a"); limiter.tryAdmit("b")
        limiter.clear()
        assertEquals(0, limiter.count())
    }
}

class FetchBackoffTest {

    @Test fun anUnknownSenderCanAlwaysBeTried() =
        assertTrue(FetchBackoff().canAttempt(1, 0))

    @Test fun waitGrowsWithEachFailureThenStaysAtTheCap() {
        val backoff = FetchBackoff(listOf(10L, 20L, 40L))
        assertEquals(0L, backoff.waitAfter(0))
        assertEquals(10L, backoff.waitAfter(1))
        assertEquals(20L, backoff.waitAfter(2))
        assertEquals(40L, backoff.waitAfter(3))
        assertEquals(40L, backoff.waitAfter(9))
    }

    @Test fun mustWaitAfterAFailure() {
        val backoff = FetchBackoff(listOf(1_000L, 2_000L))
        backoff.recordFailure(7, nowMillis = 10_000)
        assertFalse(backoff.canAttempt(7, 10_500))
        assertTrue(backoff.canAttempt(7, 11_000))
    }

    @Test fun secondFailureWaitsLonger() {
        val backoff = FetchBackoff(listOf(1_000L, 2_000L))
        backoff.recordFailure(7, 0)
        backoff.recordFailure(7, 1_000)
        assertFalse(backoff.canAttempt(7, 2_999))
        assertTrue(backoff.canAttempt(7, 3_000))
    }

    @Test fun successOrNewUpdateResetsIt() {
        val backoff = FetchBackoff(listOf(1_000L))
        backoff.recordFailure(7, 0)
        backoff.reset(7)
        assertTrue(backoff.canAttempt(7, 1))
    }

    @Test fun sendersAreIndependent() {
        val backoff = FetchBackoff(listOf(1_000L))
        backoff.recordFailure(1, 0)
        assertFalse(backoff.canAttempt(1, 10))
        assertTrue(backoff.canAttempt(2, 10))
    }

    @Test fun defaultScheduleIsGentle() {
        val d = FetchBackoff.DEFAULT_DELAYS_MILLIS
        assertEquals(listOf(5_000L, 15_000L, 45_000L, 120_000L), d)
    }
}
