package com.resq.app.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {

    @Test fun unknownSenderIsSavedAndFetched() =
        assertEquals(UpdateVerdict.SAVE_AND_FETCH, UpdateRules.decide(null, 0))

    @Test fun newerUpdateIsSavedAndFetched() =
        assertEquals(UpdateVerdict.SAVE_AND_FETCH, UpdateRules.decide(5, 6))

    @Test fun sameUpdateOnlyRefreshes() =
        assertEquals(UpdateVerdict.REFRESH_ONLY, UpdateRules.decide(5, 5))

    @Test fun olderUpdateIsIgnored() =
        assertEquals(UpdateVerdict.IGNORE, UpdateRules.decide(5, 4))

    @Test fun counterWrapsFrom65535To0AsNewer() {
        assertEquals(UpdateVerdict.SAVE_AND_FETCH, UpdateRules.decide(MAX_UPDATE_COUNTER, 0))
        assertEquals(UpdateVerdict.SAVE_AND_FETCH, UpdateRules.decide(MAX_UPDATE_COUNTER, 3))
    }

    @Test fun afterWrapTheOldHighCounterIsOlder() =
        assertEquals(UpdateVerdict.IGNORE, UpdateRules.decide(2, MAX_UPDATE_COUNTER))

    @Test fun halfwayAroundTheCircleIsTheCutOff() {
        assertTrue(UpdateRules.compare(32767, 0) > 0)
        assertTrue(UpdateRules.compare(32768, 0) < 0)
    }

    @Test fun compareIsSymmetricForNormalValues() {
        assertTrue(UpdateRules.compare(10, 9) > 0)
        assertTrue(UpdateRules.compare(9, 10) < 0)
        assertEquals(0, UpdateRules.compare(7, 7))
    }

    @Test fun lowestHopIsKept() {
        assertEquals(1, UpdateRules.lowestHop(1, 4))
        assertEquals(1, UpdateRules.lowestHop(4, 1))
        assertEquals(0, UpdateRules.lowestHop(0, 6))
    }

    @Test fun twoRelaysPassingTheSameUpdateCannotInflateTheHop() {
        // Heard directly (hop 0), then the same update comes back relayed at hop 1, 2, 3...
        var hop = 0
        for (heard in 1..10) hop = UpdateRules.lowestHop(hop, heard)
        assertEquals(0, hop)
    }
}
