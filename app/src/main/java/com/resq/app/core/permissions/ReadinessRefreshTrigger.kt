package com.resq.app.core.permissions

import android.content.Context
import com.resq.app.core.ble.BluetoothStateObserver
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.location.LocationProviderObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Every source that can make a `PermissionStatus`/`BleReadiness` snapshot
 * stale, merged into one trigger: the Bluetooth radio toggling, a
 * location provider toggling, or a runtime permission grant changing
 * (via [ServiceLocator.permissionRefreshTick], the one case with no OS
 * broadcast to listen for). `onStart` guarantees an immediate first
 * value so callers don't need a separate manual "refresh on launch" step.
 */
object ReadinessRefreshTrigger {
    /** For UI state: always has an immediate current value, then ticks on every change. */
    fun flow(context: Context, locator: ServiceLocator): Flow<Unit> =
        rawChanges(context, locator).onStart { emit(Unit) }

    /**
     * For "wait until something that could fix this changes" use sites
     * (see [com.resq.app.core.service.ResQForegroundService]'s send loop)
     * — deliberately has no immediate value, since a caller awaiting the
     * *next* change would otherwise resolve instantly off [flow]'s
     * synthetic first emission and never actually wait for anything.
     */
    fun rawChanges(context: Context, locator: ServiceLocator): Flow<Unit> = merge(
        locator.permissionRefreshTick.changesOnly(),
        BluetoothStateObserver.changes(context),
        LocationProviderObserver.changes(context),
    )
}

/**
 * Only the *next* changes of a [StateFlow]. A StateFlow hands every new collector its current value at
 * once, so without dropping that first value a "wait for the next change" returned instantly, and the send
 * loop spun hundreds of times a second whenever there was no GPS fix.
 */
internal fun StateFlow<Int>.changesOnly(): Flow<Unit> = drop(1).map { }
