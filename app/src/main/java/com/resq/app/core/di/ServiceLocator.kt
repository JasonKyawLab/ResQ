package com.resq.app.core.di

import android.content.Context
import com.resq.app.core.ble.BleAdvertiser
import com.resq.app.core.ble.BleScanner
import com.resq.app.core.ble.GattClientManager
import com.resq.app.core.ble.GattServerManager
import com.resq.app.core.battery.BatteryMonitor
import com.resq.app.core.data.db.ResQDatabase
import com.resq.app.core.data.repository.SosLogRepository
import com.resq.app.core.data.settings.SettingsRepository
import com.resq.app.core.location.LocationProvider
import com.resq.app.core.service.SessionController
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Deliberately a manual service locator, not Hilt/Dagger: this app has
 * ~8 singletons and one process, and an annotation-processor DI
 * framework would add build time and APK weight for no benefit at this
 * scale. If the dependency graph grows past what a flat object can
 * express clearly, that's the trigger to introduce Hilt — not before.
 *
 * Every dependency here is a singleton scoped to [Context.getApplicationContext],
 * safe to share between the UI process and [com.resq.app.core.service.ResQForegroundService].
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val database: ResQDatabase by lazy { ResQDatabase.getInstance(appContext) }

    val bleAdvertiser: BleAdvertiser by lazy { BleAdvertiser(appContext) }
    val bleScanner: BleScanner by lazy { BleScanner(appContext) }
    val gattServerManager: GattServerManager by lazy { GattServerManager(appContext) }
    val gattClientManager: GattClientManager by lazy { GattClientManager(appContext) }

    val locationProvider: LocationProvider by lazy { LocationProvider(appContext) }

    val sosLogRepository: SosLogRepository by lazy {
        SosLogRepository(database.sosLogDao(), gattClientManager)
    }

    val sessionController: SessionController by lazy { SessionController() }

    /**
     * Bumped after returning from a system permission/Bluetooth-enable
     * dialog (see MainActivity's ActivityResultLaunchers) — there is no
     * OS broadcast for "a runtime permission grant changed", so this is
     * the only way the Sender/Receiver/Settings screens learn about it.
     * Shared across all of them so granting from any one screen updates
     * the others without needing to revisit them.
     */
    val permissionRefreshTick: MutableStateFlow<Int> = MutableStateFlow(0)

    fun batteryPercent(): Int = BatteryMonitor.currentPercent(appContext)

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun getInstance(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
