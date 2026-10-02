package com.resq.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Build
import androidx.core.content.ContextCompat
import com.resq.app.core.util.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"
private const val FRESH_FIX_TIMEOUT_MILLIS = 20_000L

/** A GPS fix, or the most recent known-good one if a fresh request times out. */
data class GpsFix(val latitude: Double, val longitude: Double, val capturedAtEpochMillis: Long)

/**
 * Deliberately uses the platform [LocationManager] instead of a Google
 * Play Services location client: this app must work on devices without
 * GMS, and during a real emergency we can't assume Play Services is
 * installed, updated, or reachable. [requestSingleFix] is the only entry
 * point by design — the sender screen calls it once per broadcast cycle,
 * never subscribes to continuous updates, which is most of this app's
 * battery budget.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission") // caller verifies PermissionManager.hasLocationPermissions first
    suspend fun requestSingleFix(): GpsFix? {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = availableProviders(locationManager)
        if (providers.isEmpty()) {
            Logger.w(TAG) { "No enabled location provider on this device" }
            return null
        }

        // Try every enabled provider, not just the first: GPS alone can be slow
        // or signal-starved (weak indoors, cold start), and a device without
        // Play Services may only have a usable NETWORK_PROVIDER fix available.
        // Trying GPS then falling through to network beats giving up on GPS
        // alone and returning nothing for a full retry cycle.
        for (provider in providers) {
            val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MILLIS) {
                requestCurrentLocation(locationManager, provider)
            }
            if (fresh != null) return fresh.toGpsFix()
            Logger.w(TAG) { "No fresh fix from $provider within ${FRESH_FIX_TIMEOUT_MILLIS}ms" }
        }

        Logger.w(TAG) { "All providers timed out, falling back to last known location" }
        val lastKnown = providers.firstNotNullOfOrNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        return lastKnown?.toGpsFix()
    }

    /** GPS first (more accurate when it works) then network, whichever are actually enabled. */
    private fun availableProviders(locationManager: LocationManager): List<String> = listOfNotNull(
        LocationManager.GPS_PROVIDER.takeIf {
            runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
        },
        LocationManager.NETWORK_PROVIDER.takeIf {
            runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
        },
    )

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        locationManager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellationSignal = CancellationSignal()
            val executor = Executor { it.run() }
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                executor,
            ) { location -> if (continuation.isActive) continuation.resume(location) }
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
        } else {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    private fun Location.toGpsFix() = GpsFix(latitude, longitude, time)
}
