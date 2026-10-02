package com.resq.app.core.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.PermissionStatus

/**
 * Location has two independently-failing layers, and the UI needs to
 * tell them apart: a permission the user can grant in two taps, versus
 * no location provider existing on this hardware at all (some
 * BLE-only/tablet devices genuinely have no GPS or network location).
 * The latter is what should steer someone toward typing their location
 * into the SOS message by hand instead of waiting on a fix that will
 * never arrive.
 */
object LocationCapability {

    fun hasLocationHardware(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

    fun permissionStatus(context: Context): PermissionStatus = when {
        !hasLocationHardware(context) -> PermissionStatus.UNAVAILABLE
        PermissionManager.hasLocationPermissions(context) -> PermissionStatus.GRANTED
        else -> PermissionStatus.DENIED
    }

    /** Is there a provider on this device, and is it currently switched on? */
    fun serviceStatus(context: Context): PermissionStatus {
        val locationManager = context.getSystemService(LocationManager::class.java)
            ?: return PermissionStatus.UNAVAILABLE
        val hasAnyProvider = runCatching { locationManager.allProviders.isNotEmpty() }.getOrDefault(false)
        if (!hasAnyProvider) return PermissionStatus.UNAVAILABLE

        val enabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        return if (enabled) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    /** True whenever an automatic GPS fix can't be expected to work — permission, hardware, or service. */
    fun isUnreliable(context: Context): Boolean =
        permissionStatus(context) != PermissionStatus.GRANTED ||
            serviceStatus(context) != PermissionStatus.GRANTED
}
