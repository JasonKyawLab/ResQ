package com.resq.app.core.ble

import java.util.UUID

/**
 * BLE identifiers for the ResQ mesh.
 *
 * Two different UUID spaces are deliberate, not an oversight:
 *
 * - [BEACON_SERVICE_UUID_16] rides inside the legacy advertisement itself
 *   (31-byte budget total), where a 128-bit UUID alone would eat most of
 *   the packet. We use a 16-bit value from the Bluetooth SIG's
 *   "reserved for development/private use" block (0xFFF0-0xFFFC) — the
 *   standard convention for closed, single-app BLE ecosystems that don't
 *   register with the SIG. A collision with an unrelated app using the
 *   same block is possible but harmless: [BlePayloadCodec] validates the
 *   payload shape and version byte before trusting it.
 * - [GATT_SERVICE_UUID] / [GATT_SOS_RECORD_CHARACTERISTIC_UUID] are only
 *   ever exchanged after a GATT connection is already established, where
 *   packet-size pressure doesn't apply, so a globally-unique 128-bit UUID
 *   is used as normal.
 */
object BleConstants {

    const val BEACON_SERVICE_UUID_16: Int = 0xFFF1

    val GATT_SERVICE_UUID: UUID = UUID.fromString("8f1a2b3c-4d5e-6f70-8192-a3b4c5d6e7f0")
    val GATT_SOS_RECORD_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("8f1a2b3c-4d5e-6f70-8192-a3b4c5d6e7f1")

    /** Standard descriptor every notifying characteristic needs for the GATT spec's sake. */
    val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun beaconServiceParcelUuid(): android.os.ParcelUuid =
        android.os.ParcelUuid.fromString(
            String.format("0000%04x-0000-1000-8000-00805f9b34fb", BEACON_SERVICE_UUID_16)
        )
}
