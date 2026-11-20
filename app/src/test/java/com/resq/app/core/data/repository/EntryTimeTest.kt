package com.resq.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryTimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    // --- heardAgeMillis ---

    @Test fun normalCaseUsesTheTimeSinceBoot() {
        // Phone up for 10 days; entry saved 5 minutes ago.
        val nowElapsed = 10 * day
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = 1_000_000_000L,
            lastSeenElapsedMillis = nowElapsed - 5 * minute,
            nowWallMillis = 1_000_000_000L + 5 * minute,
            nowElapsedMillis = nowElapsed,
        )
        assertEquals(5 * minute, age)
    }

    @Test fun clockChangedForwardTwoHoursDoesNotMoveTheAge() {
        val nowElapsed = 10 * day
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = 1_000_000_000L,
            lastSeenElapsedMillis = nowElapsed - 5 * minute,
            nowWallMillis = 1_000_000_000L + 5 * minute + 2 * hour, // user moved the clock forward
            nowElapsedMillis = nowElapsed,
        )
        assertEquals(5 * minute, age)
    }

    @Test fun clockChangedBackwardDoesNotGiveANegativeAge() {
        val nowElapsed = 10 * day
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = 1_000_000_000L,
            lastSeenElapsedMillis = nowElapsed - 5 * minute,
            nowWallMillis = 1_000_000_000L - 3 * hour,
            nowElapsedMillis = nowElapsed,
        )
        assertEquals(5 * minute, age)
    }

    @Test fun savedBeforeARebootFallsBackToTheWallClock() {
        // Saved late in a long uptime, so its saved value is larger than the new uptime.
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = 1_000_000_000L,
            lastSeenElapsedMillis = 5 * day,
            nowWallMillis = 1_000_000_000L + 2 * day,
            nowElapsedMillis = 3 * hour,
        )
        assertEquals(2 * day, age)
    }

    @Test fun savedEarlyBeforeARebootIsStillCaughtByTheWallClockCheck() {
        // Saved 2 minutes into the old uptime (so the saved value looks plausible), then a reboot.
        val age = EntryLifecycle.heardAgeMillis(
            lastSeenWallMillis = 1_000_000_000L,
            lastSeenElapsedMillis = 2 * minute,
            nowWallMillis = 1_000_000_000L + 3 * hour,
            nowElapsedMillis = 1 * hour,
        )
        assertEquals(3 * hour, age)
    }

    @Test fun rowsFromBeforeTheColumnExistedUseTheWallClock() =
        assertEquals(
            4 * minute,
            EntryLifecycle.heardAgeMillis(
                lastSeenWallMillis = 5_000L,
                lastSeenElapsedMillis = 0L,
                nowWallMillis = 5_000L + 4 * minute,
                nowElapsedMillis = 10 * day,
            ),
        )

    // --- positionAgeMillis ---

    @Test fun positionAgeFromTheSendersTime() {
        val now = 1_800_000_000_000L
        assertEquals(4 * minute, EntryLifecycle.positionAgeMillis(now / 1000 - 240, now))
    }

    @Test fun unknownPositionTimeGivesNoAge() =
        assertNull(EntryLifecycle.positionAgeMillis(null, 1_800_000_000_000L))

    @Test fun aPositionTimeInOurFutureMeansTheClocksDisagree() {
        val now = 1_800_000_000_000L
        assertNull(EntryLifecycle.positionAgeMillis(now / 1000 + 3600, now))
    }

    @Test fun smallClockDifferenceIsToleratedAndShownAsJustNow() {
        val now = 1_800_000_000_000L
        assertEquals(0L, EntryLifecycle.positionAgeMillis(now / 1000 + 60, now))
    }

    // --- text ---

    @Test fun heardLineChoosesTheRightShape() {
        assertEquals("Heard just now", EntryLifecycle.heardLine(20_000, "20:59", "Nov 19, 21:59"))
        assertEquals("Heard 12 min ago", EntryLifecycle.heardLine(12 * minute, "20:59", "Nov 19, 21:59"))
        assertEquals("Heard 3 h ago (20:59)", EntryLifecycle.heardLine(3 * hour + 5 * minute, "20:59", "Nov 19, 21:59"))
        assertEquals("Heard 1 day ago (Nov 19, 21:59)", EntryLifecycle.heardLine(25 * hour, "20:59", "Nov 19, 21:59"))
        assertEquals("Heard 2 days ago (Nov 19, 21:59)", EntryLifecycle.heardLine(50 * hour, "20:59", "Nov 19, 21:59"))
    }

    @Test fun positionLineText() {
        assertNull(EntryLifecycle.positionLine(null))
        assertEquals("Position just now", EntryLifecycle.positionLine(10_000))
        assertEquals("Position 4 min old", EntryLifecycle.positionLine(4 * minute))
        assertEquals("Position 3 h old", EntryLifecycle.positionLine(3 * hour))
    }
}
