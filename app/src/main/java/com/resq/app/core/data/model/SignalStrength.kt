package com.resq.app.core.data.model

/**
 * Coarse bucket for a raw BLE RSSI reading (dBm, typically -30..-100).
 * The receiver log shows this instead of a raw dBm number, which means
 * nothing to someone glancing at a phone during an emergency.
 */
enum class SignalStrength(val label: String) {
    STRONG("Strong"),
    MEDIUM("Medium"),
    WEAK("Weak");

    companion object {
        fun fromRssi(rssi: Int): SignalStrength = when {
            rssi >= -70 -> STRONG
            rssi >= -90 -> MEDIUM
            else -> WEAK
        }
    }
}
