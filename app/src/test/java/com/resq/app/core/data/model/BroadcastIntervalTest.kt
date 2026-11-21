package com.resq.app.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BroadcastIntervalTest {
    @Test fun hourlyIsNotAChoiceButStillWhatBatterySaverUses() {
        assertFalse(BroadcastInterval.HOURLY in BroadcastInterval.SELECTABLE)
        assertEquals(BroadcastInterval.HOURLY, BroadcastInterval.BATTERY_SAVER)
    }

    @Test fun anOlderSavedHourlyChoiceFallsBackToTheDefault() =
        assertEquals(BroadcastInterval.DEFAULT, BroadcastInterval.fromMinutes(60))

    @Test fun theOfferedChoicesAreKept() {
        assertEquals(BroadcastInterval.EVERY_1_MIN, BroadcastInterval.fromMinutes(1))
        assertEquals(BroadcastInterval.EVERY_15_MIN, BroadcastInterval.fromMinutes(15))
    }
}
