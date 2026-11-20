package com.resq.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryLifecycleTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test fun liveWindowIsTwiceTheIntervalWithinBounds() {
        assertEquals(30 * minute, EntryLifecycle.liveWindowMillis(15))
        assertEquals(10 * minute, EntryLifecycle.liveWindowMillis(5))   // 2 x 5 = 10
        assertEquals(10 * minute, EntryLifecycle.liveWindowMillis(1))   // raised to the 10 minute floor
        assertEquals(2 * hour, EntryLifecycle.liveWindowMillis(60))
        assertEquals(2 * hour, EntryLifecycle.liveWindowMillis(600))    // capped
    }

    @Test fun unknownIntervalUsesTheDefault() {
        assertEquals(30 * minute, EntryLifecycle.liveWindowMillis(null))
        assertEquals(30 * minute, EntryLifecycle.liveWindowMillis(0))
    }

    @Test fun recentEntryIsLive() =
        assertEquals(EntryStage.LIVE, EntryLifecycle.stage(false, 2 * minute, 5))

    @Test fun entryNotHeardForAWhileIsSignalLost() =
        assertEquals(EntryStage.SIGNAL_LOST, EntryLifecycle.stage(false, 3 * hour, 5))

    @Test fun windowEdgeStaysLive() {
        assertEquals(EntryStage.LIVE, EntryLifecycle.stage(false, 10 * minute, 5))
        assertEquals(EntryStage.SIGNAL_LOST, EntryLifecycle.stage(false, 10 * minute + 1, 5))
    }

    @Test fun cancelledEntryIsMarkedSafeWhateverItsAge() {
        assertEquals(EntryStage.MARKED_SAFE, EntryLifecycle.stage(true, 1 * minute, 5))
        assertEquals(EntryStage.MARKED_SAFE, EntryLifecycle.stage(true, 20 * hour, 5))
    }

    @Test fun veryOldEntriesAreArchivedEvenIfCancelled() {
        assertEquals(EntryStage.ARCHIVED, EntryLifecycle.stage(false, 48 * hour, 5))
        assertEquals(EntryStage.ARCHIVED, EntryLifecycle.stage(true, 60 * hour, 5))
    }

    @Test fun stagesSortLiveFirst() {
        val sorted = listOf(EntryStage.ARCHIVED, EntryStage.LIVE, EntryStage.MARKED_SAFE, EntryStage.SIGNAL_LOST).sorted()
        assertEquals(
            listOf(EntryStage.LIVE, EntryStage.SIGNAL_LOST, EntryStage.MARKED_SAFE, EntryStage.ARCHIVED),
            sorted,
        )
    }

    @Test fun onlyLiveEntriesAreRelayed() {
        assertTrue(EntryLifecycle.shouldRelay(EntryStage.LIVE, minute, false))
        assertFalse(EntryLifecycle.shouldRelay(EntryStage.SIGNAL_LOST, 3 * hour, false))
        assertFalse(EntryLifecycle.shouldRelay(EntryStage.ARCHIVED, 50 * hour, false))
    }

    @Test fun aFreshMarkedSafeIsRelayedBrieflyThenNot() {
        assertTrue(EntryLifecycle.shouldRelay(EntryStage.MARKED_SAFE, 10 * minute, false))
        assertFalse(EntryLifecycle.shouldRelay(EntryStage.MARKED_SAFE, 16 * minute, false))
    }

    @Test fun aDismissedEntryIsNeverRelayed() =
        assertFalse(EntryLifecycle.shouldRelay(EntryStage.LIVE, minute, true))

    @Test fun ageTextIsShort() {
        assertEquals("just now", EntryLifecycle.ageText(20_000))
        assertEquals("1 min", EntryLifecycle.ageText(minute))
        assertEquals("59 min", EntryLifecycle.ageText(59 * minute))
        assertEquals("1 h", EntryLifecycle.ageText(60 * minute))
        assertEquals("3 h", EntryLifecycle.ageText(3 * hour + 20 * minute))
        assertEquals("47 h", EntryLifecycle.ageText(47 * hour))
        assertEquals("2 days", EntryLifecycle.ageText(49 * hour))
        assertEquals("just now", EntryLifecycle.ageText(-5_000))
    }
}
