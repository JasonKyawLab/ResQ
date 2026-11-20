package com.resq.app.debug

import com.resq.app.core.di.ServiceLocator
import com.resq.app.ui.components.DebugAction

/** Release build: no testing buttons. The real helper lives in `src/debug` and is not compiled here. */
object DemoTools {
    @Suppress("UNUSED_PARAMETER")
    fun actions(locator: ServiceLocator): List<DebugAction> = emptyList()
}
