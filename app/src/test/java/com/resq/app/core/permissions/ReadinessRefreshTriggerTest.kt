package com.resq.app.core.permissions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadinessRefreshTriggerTest {

    @Test fun waitingForTheNextChangeDoesNotReturnAtOnce() = runBlocking {
        // This is the bug that spun the send loop: a StateFlow used to resolve instantly.
        val tick = MutableStateFlow(0)
        val result = withTimeoutOrNull(300) { tick.changesOnly().first() }
        assertNull(result)
    }

    @Test fun aRealChangeIsStillSeen() = runBlocking {
        val tick = MutableStateFlow(0)
        val job = launch { delay(100); tick.value = 1 }
        val result = withTimeoutOrNull(2_000) { tick.changesOnly().first() }
        job.join()
        assertNotNull(result)
    }
}
