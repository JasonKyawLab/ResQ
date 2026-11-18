package com.resq.app.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Wire protocol for the ResQ mesh, versioned from day one.
 *
 * [BeaconPayload] is the small, always-on broadcast every scanner can
 * see without connecting to anything: who, roughly where, and whether a
 * fuller record is worth connecting for. [SosRecord] is the fuller
 * record pulled over GATT once a scanner decides to connect.
 *
 * [PROTOCOL_VERSION] is the first byte of both encodings. A future
 * format change bumps this constant and adds a new `decodeVX` branch
 * below; [BlePayloadCodec] already drops anything with an unrecognised
 * version instead of crashing, so older and newer app builds can share
 * the air without taking each other down.
 */
const val PROTOCOL_VERSION: Int = 1

/** Bit flags packed into [BeaconPayload.flags]. */
object BeaconFlags {
    const val HAS_FULL_MESSAGE: Int = 1 shl 0
    const val IS_RELAYED: Int = 1 shl 1
    const val BATTERY_CRITICAL: Int = 1 shl 2
}

/** Fixed-point scale applied to lat/lon before packing into an Int (≈1.1 m precision). */
private const val COORDINATE_SCALE = 100_000.0

/** Hard cap on relay hops so a single SOS can't circulate the mesh forever. */
const val MAX_HOP_COUNT: Int = 6

/** Max UTF-8 bytes for a free-text message, chosen to clear one GATT read comfortably. */
const val MAX_MESSAGE_BYTES: Int = 180

/**
 * Longest prefix of this string that fits in [maxBytes] when UTF-8 encoded,
 * never cutting a character (or emoji) in half the way slicing raw bytes would.
 */
fun String.truncateUtf8(maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < length) {
        val codePoint = codePointAt(end)
        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(codePoint)
    }
    return substring(0, end)
}

/**
 * The compact payload broadcast in the BLE advertisement's service data.
 * Fixed 16-byte layout — do not reorder fields without bumping
 * [PROTOCOL_VERSION], since older receivers decode this positionally.
 */
data class BeaconPayload(
    val senderId: Int,
    val latitude: Double,
    val longitude: Double,
    val flags: Int,
    val hopCount: Int,
    val sequence: Int,
) {
    val hasFullMessage: Boolean get() = flags and BeaconFlags.HAS_FULL_MESSAGE != 0
    val isRelayed: Boolean get() = flags and BeaconFlags.IS_RELAYED != 0
    val batteryCritical: Boolean get() = flags and BeaconFlags.BATTERY_CRITICAL != 0
}

/** The full record exchanged over GATT once a scanner connects to a beacon. */
data class SosRecord(
    val senderId: Int,
    val latitude: Double,
    val longitude: Double,
    val message: String,
    val batteryPercent: Int,
    val hopCount: Int,
)

object BlePayloadCodec {

    private const val BEACON_SIZE = 16

    fun encodeBeacon(payload: BeaconPayload): ByteArray {
        val buffer = ByteBuffer.allocate(BEACON_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.putInt(payload.senderId)
        buffer.putInt((payload.latitude * COORDINATE_SCALE).roundToInt())
        buffer.putInt((payload.longitude * COORDINATE_SCALE).roundToInt())
        buffer.put(payload.flags.toByte())
        buffer.put(payload.hopCount.coerceIn(0, MAX_HOP_COUNT).toByte())
        buffer.put(payload.sequence.toByte())
        return buffer.array()
    }

    fun decodeBeacon(bytes: ByteArray): BeaconPayload? {
        if (bytes.size < BEACON_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get().toInt() and 0xFF
        if (version != PROTOCOL_VERSION) return null // unknown format — ignore, don't crash
        val senderId = buffer.int
        val lat = buffer.int / COORDINATE_SCALE
        val lon = buffer.int / COORDINATE_SCALE
        val flags = buffer.get().toInt() and 0xFF
        val hopCount = buffer.get().toInt() and 0xFF
        val sequence = buffer.get().toInt() and 0xFF
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return BeaconPayload(senderId, lat, lon, flags, hopCount, sequence)
    }

    fun encodeSosRecord(record: SosRecord): ByteArray {
        val messageBytes = record.message.truncateUtf8(MAX_MESSAGE_BYTES).toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + 4 + 4 + 2 + messageBytes.size + 1 + 1)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.putInt(record.senderId)
        buffer.putInt((record.latitude * COORDINATE_SCALE).roundToInt())
        buffer.putInt((record.longitude * COORDINATE_SCALE).roundToInt())
        buffer.putShort(messageBytes.size.toShort())
        buffer.put(messageBytes)
        buffer.put(record.batteryPercent.coerceIn(0, 100).toByte())
        buffer.put(record.hopCount.coerceIn(0, MAX_HOP_COUNT).toByte())
        return buffer.array()
    }

    fun decodeSosRecord(bytes: ByteArray): SosRecord? {
        if (bytes.size < 1 + 4 + 4 + 4 + 2) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get().toInt() and 0xFF
        if (version != PROTOCOL_VERSION) return null
        val senderId = buffer.int
        val lat = buffer.int / COORDINATE_SCALE
        val lon = buffer.int / COORDINATE_SCALE
        val messageLength = buffer.short.toInt() and 0xFFFF
        if (messageLength > MAX_MESSAGE_BYTES || buffer.remaining() < messageLength + 2) return null
        val messageBytes = ByteArray(messageLength)
        buffer.get(messageBytes)
        val message = String(messageBytes, Charsets.UTF_8)
        val batteryPercent = buffer.get().toInt() and 0xFF
        val hopCount = buffer.get().toInt() and 0xFF
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return SosRecord(senderId, lat, lon, message, batteryPercent, hopCount)
    }
}
