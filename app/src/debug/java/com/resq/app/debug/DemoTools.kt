package com.resq.app.debug

import com.resq.app.core.di.ServiceLocator
import com.resq.app.ui.components.DebugAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Testing buttons for the Scan screen (debug builds). The release build has a copy of this object
 * in `src/release` that returns no actions, so nothing here ships.
 */
object DemoTools {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun actions(locator: ServiceLocator): List<DebugAction> {
        val dao = locator.database.sosLogDao()
        return listOf(
            DebugAction("Add demo SOS entries") {
                scope.launch {
                    // An upsert also brings back any demo entry that was dismissed.
                    DemoEntries.build(System.currentTimeMillis()).forEach { dao.upsert(it) }
                }
            },
            DebugAction("Remove demo entries") {
                scope.launch { dao.deleteRange(DemoEntries.ID_FROM, DemoEntries.ID_TO) }
            },
        )
    }
}
