package com.resq.app.core.data.model

import androidx.annotation.StringRes
import com.resq.app.R

/**
 * Coarse bucket for a raw BLE RSSI reading (dBm, typically -30..-100).
 * The receiver log shows this instead of a raw dBm number, which means
 * nothing to someone glancing at a phone during an emergency.
 */
enum class SignalStrength(@StringRes val labelRes: Int) {
    STRONG(R.string.signal_strong),
    MEDIUM(R.string.signal_medium),
    WEAK(R.string.signal_weak);

    companion object {
        fun fromRssi(rssi: Int): SignalStrength = when {
            rssi >= -70 -> STRONG
            rssi >= -90 -> MEDIUM
            else -> WEAK
        }
    }
}
