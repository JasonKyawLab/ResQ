package com.resq.app.core.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.resq.app.core.permissions.PermissionManager

/** What ResQ can currently do with the radio on this device, checked before every BLE op. */
sealed interface BleReadiness {
    data object Ready : BleReadiness
    data object NoAdapter : BleReadiness
    data object AdapterDisabled : BleReadiness
    data object MissingPermissions : BleReadiness
    data object AdvertisingUnsupported : BleReadiness
}

object BleCapability {

    private fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    fun readinessToScan(context: Context): BleReadiness {
        val bt = adapter(context) ?: return BleReadiness.NoAdapter
        if (!bt.isEnabled) return BleReadiness.AdapterDisabled
        if (!PermissionManager.hasBluetoothPermissions(context)) return BleReadiness.MissingPermissions
        return BleReadiness.Ready
    }

    fun readinessToAdvertise(context: Context): BleReadiness {
        val scanReadiness = readinessToScan(context)
        if (scanReadiness != BleReadiness.Ready) return scanReadiness
        val bt = adapter(context) ?: return BleReadiness.NoAdapter
        if (!bt.isMultipleAdvertisementSupported) return BleReadiness.AdvertisingUnsupported
        return BleReadiness.Ready
    }

    fun isBluetoothEnabled(context: Context): Boolean = adapter(context)?.isEnabled == true
}
