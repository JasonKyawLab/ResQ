package com.resq.app.core.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.PermissionStatus

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

    fun hasBleHardware(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /** Is the radio itself on? Distinct from whether we're allowed to use it — see [bluetoothPermissionStatus]. */
    fun adapterStatus(context: Context): PermissionStatus = when {
        !hasBleHardware(context) -> PermissionStatus.UNAVAILABLE
        adapter(context) == null -> PermissionStatus.UNAVAILABLE
        adapter(context)?.isEnabled != true -> PermissionStatus.DENIED
        else -> PermissionStatus.GRANTED
    }

    fun bluetoothPermissionStatus(context: Context): PermissionStatus = when {
        !hasBleHardware(context) -> PermissionStatus.UNAVAILABLE
        PermissionManager.hasBluetoothPermissions(context) -> PermissionStatus.GRANTED
        else -> PermissionStatus.DENIED
    }
}
