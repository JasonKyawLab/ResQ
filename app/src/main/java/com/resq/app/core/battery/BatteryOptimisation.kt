package com.resq.app.core.battery

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Android's battery optimisation can stop a background app. Reading whether ResQ is exempt needs no
 * permission, and opening the system page needs none either. The restricted "ignore battery
 * optimisations" permission, which would let ResQ ask for the exemption itself, is deliberately not used
 * until Google Play's policy for it has been checked.
 */
object BatteryOptimisation {

    /** True when Android will not restrict ResQ in the background. */
    fun isExempt(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return true
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system page listing apps and their battery optimisation, or ResQ's own settings page if a
     * phone has no such page. Tries and catches rather than asking first, because on Android 11 and later
     * an app cannot always see whether a system page exists.
     */
    fun openSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
    }
}
