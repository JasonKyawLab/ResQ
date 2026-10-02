package com.resq.app.core.permissions

import android.content.Context
import com.resq.app.core.ble.BluetoothStateObserver
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.location.LocationProviderObserver
import kotlinx.coroutines.flow.Flow
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
    fun flow(context: Context, locator: ServiceLocator): Flow<Unit> = merge(
        locator.permissionRefreshTick.map { },
        BluetoothStateObserver.changes(context),
        LocationProviderObserver.changes(context),
    ).onStart { emit(Unit) }
}
