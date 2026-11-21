package com.resq.app.core.battery

import android.content.Context
import android.os.BatteryManager

/**
 * Battery level as a plain snapshot read, not a registered broadcast
 * receiver — [BatteryManager.BATTERY_PROPERTY_CAPACITY] is a cheap
 * synchronous system-service call, so there's no reason to keep a
 * receiver alive (and draining a little battery of its own) just to
 * answer "what's the level right now" once per broadcast cycle.
 */
object BatteryMonitor {

    private const val CRITICAL_THRESHOLD_PERCENT = 15

    fun currentPercent(context: Context): Int {
        val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return -1
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level.coerceIn(0, 100)
    }

    /** True when [percent] (as read by [currentPercent]) is low enough to tell helpers the phone is nearly flat. */
    fun isCritical(percent: Int): Boolean = percent in 0..CRITICAL_THRESHOLD_PERCENT
}
