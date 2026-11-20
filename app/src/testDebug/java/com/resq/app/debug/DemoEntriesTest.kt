package com.resq.app.debug

import com.resq.app.core.data.repository.EntryLifecycle
import com.resq.app.core.data.repository.EntryStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoEntriesTest {

    private val now = 1_800_000_000_000L
    private val entries = DemoEntries.build(now, nowElapsedMillis = 10_000_000_000L)

    @Test fun everyStageIsRepresented() {
        val stages = entries.map {
            EntryLifecycle.stage(it.isCancelled, now - it.lastSeenAtEpochMillis, it.sendIntervalMinutes)
        }.toSet()
        assertEquals(EntryStage.entries.toSet(), stages)
    }

    @Test fun allIdsAreInTheDemoRangeSoRemovalFindsThem() {
        assertTrue(entries.all { it.senderId in DemoEntries.ID_FROM..DemoEntries.ID_TO })
        assertEquals(entries.size, entries.map { it.senderId }.toSet().size)
    }

    @Test fun thereIsALocationOnlyAndARelayedEntry() {
        assertTrue(entries.any { !it.hasFullMessage && it.message.isEmpty() })
        assertTrue(entries.any { it.isRelayed && it.hopCount > 0 })
    }

    @Test fun demoEntriesAreNotDismissedAndCoordinatesAreValid() {
        assertTrue(entries.none { it.dismissed })
        assertTrue(entries.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
    }
}
