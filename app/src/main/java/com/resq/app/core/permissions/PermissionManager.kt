package com.resq.app.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single source of truth for which runtime permissions ResQ needs and
 * whether it currently has them. The permission set genuinely differs
 * by API level (BLUETOOTH_SCAN/ADVERTISE/CONNECT only exist from 31,
 * POST_NOTIFICATIONS only from 33), so every call site asks this object
 * instead of hardcoding a permission list that would break on old or
 * new devices.
 */
object PermissionManager {

    /** Permissions required for BLE advertising + scanning + GATT. */
    fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // Pre-31: BLUETOOTH/BLUETOOTH_ADMIN are install-time (normal) permissions,
            // but scan results are withheld without a granted location permission.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    /** Location is required for BLE scan results on every API level we target. */
    fun locationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun notificationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    /** Everything the app needs to run both sender and receiver roles. */
    fun allRequired(): Array<String> =
        (bluetoothPermissions().toSet() + locationPermissions() + notificationPermissions())
            .toTypedArray()

    fun hasAll(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBluetoothPermissions(context: Context): Boolean =
        hasAll(context, bluetoothPermissions())

    fun hasLocationPermissions(context: Context): Boolean =
        hasAll(context, locationPermissions())

    fun hasNotificationPermission(context: Context): Boolean =
        hasAll(context, notificationPermissions())

    fun hasEverythingRequired(context: Context): Boolean =
        hasBluetoothPermissions(context) && hasLocationPermissions(context) &&
            hasNotificationPermission(context)
}
