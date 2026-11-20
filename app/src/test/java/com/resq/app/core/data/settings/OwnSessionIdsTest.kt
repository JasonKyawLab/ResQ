package com.resq.app.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSessionIdsTest {

    @Test fun newIdsAreNonZeroAndDiffer() {
        val ids = (1..50).map { OwnSessionIds.newSenderId() }
        assertTrue(ids.all { it > 0 })
        assertTrue(ids.toSet().size > 1)
    }

    @Test fun rememberedIdIsOwn() {
        val stored = OwnSessionIds.remember(null, 42, 1000)
        assertTrue(OwnSessionIds.isOwn(stored, 42, 2000))
        assertFalse(OwnSessionIds.isOwn(stored, 43, 2000))
    }

    @Test fun idIsForgottenAfter72Hours() {
        val stored = OwnSessionIds.remember(null, 42, 0)
        assertTrue(OwnSessionIds.isOwn(stored, 42, OWN_ID_MEMORY_SECONDS))
        assertFalse(OwnSessionIds.isOwn(stored, 42, OWN_ID_MEMORY_SECONDS + 1))
    }

    @Test fun rememberDropsExpiredAndKeepsRecent() {
        var stored = OwnSessionIds.remember(null, 1, 0)
        stored = OwnSessionIds.remember(stored, 2, OWN_ID_MEMORY_SECONDS + 10)
        assertEquals(listOf(2 to OWN_ID_MEMORY_SECONDS + 10), OwnSessionIds.parse(stored))
    }

    @Test fun rememberingSameIdDoesNotDuplicate() {
        var stored = OwnSessionIds.remember(null, 7, 10)
        stored = OwnSessionIds.remember(stored, 7, 20)
        assertEquals(listOf(7 to 20L), OwnSessionIds.parse(stored))
    }

    @Test fun garbageIsIgnored() {
        assertEquals(listOf(5 to 9L), OwnSessionIds.parse("junk,5:9,x:y,1:2:3,"))
        assertTrue(OwnSessionIds.parse(null).isEmpty())
        assertNotEquals(true, OwnSessionIds.isOwn("", 1, 0))
    }
}

class SessionResumeTest {
    @Test fun recentActiveSessionResumes() =
        assertTrue(SessionResume.isResumable(true, 1000, 1000 + 3600))

    @Test fun stoppedSessionDoesNotResume() =
        assertFalse(SessionResume.isResumable(false, 1000, 1100))

    @Test fun exactlyTwelveHoursStillResumesButOneSecondMoreDoesNot() {
        assertTrue(SessionResume.isResumable(true, 0 + 1, 1 + RESUME_WINDOW_SECONDS))
        assertFalse(SessionResume.isResumable(true, 1, 2 + RESUME_WINDOW_SECONDS))
    }

    @Test fun neverSavedOrClockBackwardsDoesNotResume() {
        assertFalse(SessionResume.isResumable(true, 0, 500))
        assertFalse(SessionResume.isResumable(true, 2000, 1000))
    }
}
