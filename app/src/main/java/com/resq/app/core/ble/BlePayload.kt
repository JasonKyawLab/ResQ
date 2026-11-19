package com.resq.app.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Wire protocol for the ResQ mesh. The plan and the byte-by-byte tables live in
 * ROADMAP.md; this file is the code form of them.
 *
 * [BeaconPayload] is the small, always-on broadcast every scanner can
 * see without connecting to anything: who, roughly where, and whether a
 * fuller record is worth connecting for. [SosRecord] is the fuller
 * record pulled over GATT once a scanner decides to connect.
 *
 * Both start with one header byte: the high half is the fixed ResQ marker
 * ([PROTOCOL_MARKER]) and the low half is [PROTOCOL_VERSION]. A packet with
 * another marker is not ours and is dropped; a version we do not know is
 * ignored, never a crash, so older and newer builds can share the air.
 *
 * The format can still grow after release, by two rules (details in the
 * ROADMAP "Format rules"): new record information is a new extra type, and new
 * beacon information is a new flag bit. Never reorder, resize or reuse a field.
 */
const val PROTOCOL_VERSION: Int = 1

/** High half of the header byte: marks a packet as ResQ's. */
const val PROTOCOL_MARKER: Int = 0xA0

private const val HEADER_BYTE: Int = PROTOCOL_MARKER or PROTOCOL_VERSION

/** Bit flags packed into [BeaconPayload.flags]. Bits 32, 64 and 128 are reserved. */
object BeaconFlags {
    const val HAS_FULL_MESSAGE: Int = 1 shl 0
    const val IS_RELAYED: Int = 1 shl 1
    const val BATTERY_CRITICAL: Int = 1 shl 2
    const val CANCELLED: Int = 1 shl 3
    const val POSITION_UNKNOWN: Int = 1 shl 4

    /** Everything defined so far. Reserved bits are written as 0 and ignored on read. */
    const val KNOWN_MASK: Int = 0x1F
}

/** Types of the optional `type, length, value` items at the end of a [SosRecord]. */
object RecordExtra {
    /** Sender's clock when the fix was taken: 4 bytes, seconds since 1970 UTC. */
    const val POSITION_TIME: Int = 0x01

    /** Sender's clock when the user pressed Start: 4 bytes, seconds since 1970 UTC. */
    const val SOS_STARTED: Int = 0x02

    /** Minutes between the sender's updates: 1 byte. */
    const val SEND_INTERVAL: Int = 0x03
}

/** Fixed-point scale applied to lat/lon before packing into an Int (≈1.1 m precision). */
private const val COORDINATE_SCALE = 100_000.0

/** Hard cap on relay hops so a single SOS can't circulate the mesh forever. */
const val MAX_HOP_COUNT: Int = 6

/** Max UTF-8 bytes for a free-text message, chosen to clear one GATT read comfortably. */
const val MAX_MESSAGE_BYTES: Int = 180

/** The update counter is 2 bytes, so it tops out here. */
const val MAX_UPDATE_COUNTER: Int = 0xFFFF

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
 * Fixed 17-byte layout. Do not reorder or resize fields: older receivers
 * decode this positionally. New information goes in a reserved flag bit.
 *
 * [updateCounter] goes up only when the sender takes a new fix or edits the
 * message, so a phone can tell "same update" from "newer update" without
 * connecting. When [BeaconFlags.POSITION_UNKNOWN] is set, [latitude] and
 * [longitude] carry no meaning and are sent as 0.
 */
data class BeaconPayload(
    val senderId: Int,
    val latitude: Double,
    val longitude: Double,
    val flags: Int,
    val hopCount: Int,
    val updateCounter: Int,
) {
    val hasFullMessage: Boolean get() = flags and BeaconFlags.HAS_FULL_MESSAGE != 0
    val isRelayed: Boolean get() = flags and BeaconFlags.IS_RELAYED != 0
    val batteryCritical: Boolean get() = flags and BeaconFlags.BATTERY_CRITICAL != 0
    val isCancelled: Boolean get() = flags and BeaconFlags.CANCELLED != 0
    val positionUnknown: Boolean get() = flags and BeaconFlags.POSITION_UNKNOWN != 0
}

/**
 * The full record exchanged over GATT once a scanner connects to a beacon.
 * The three extras are null when the sender did not include them (an older
 * build, or a relay that does not have them yet).
 */
data class SosRecord(
    val senderId: Int,
    val latitude: Double,
    val longitude: Double,
    val message: String,
    val batteryPercent: Int,
    val hopCount: Int,
    val positionTimeSeconds: Long? = null,
    val sosStartedSeconds: Long? = null,
    val sendIntervalMinutes: Int? = null,
)

object BlePayloadCodec {

    const val BEACON_SIZE = 17

    /** version, id, lat, lon, message length: everything before the message. */
    private const val RECORD_FIXED_HEAD = 1 + 4 + 4 + 4 + 2

    fun encodeBeacon(payload: BeaconPayload): ByteArray {
        val buffer = ByteBuffer.allocate(BEACON_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(HEADER_BYTE.toByte())
        buffer.putInt(payload.senderId)
        buffer.putInt((payload.latitude * COORDINATE_SCALE).roundToInt())
        buffer.putInt((payload.longitude * COORDINATE_SCALE).roundToInt())
        buffer.put((payload.flags and BeaconFlags.KNOWN_MASK).toByte())
        buffer.put(payload.hopCount.coerceIn(0, MAX_HOP_COUNT).toByte())
        buffer.putShort(payload.updateCounter.coerceIn(0, MAX_UPDATE_COUNTER).toShort())
        return buffer.array()
    }

    /** Returns null for anything that is not a ResQ beacon we understand. */
    fun decodeBeacon(bytes: ByteArray): BeaconPayload? {
        // A longer packet is fine: a later version may append to it.
        if (bytes.size < BEACON_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!isKnownHeader(buffer.get().toInt() and 0xFF)) return null
        val senderId = buffer.int
        val lat = buffer.int / COORDINATE_SCALE
        val lon = buffer.int / COORDINATE_SCALE
        val flags = buffer.get().toInt() and 0xFF
        val hopCount = buffer.get().toInt() and 0xFF
        val updateCounter = buffer.short.toInt() and 0xFFFF
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return BeaconPayload(senderId, lat, lon, flags, hopCount, updateCounter)
    }

    fun encodeSosRecord(record: SosRecord): ByteArray {
        val messageBytes = record.message.truncateUtf8(MAX_MESSAGE_BYTES).toByteArray(Charsets.UTF_8)
        val extras = encodeExtras(record)
        val buffer = ByteBuffer
            .allocate(RECORD_FIXED_HEAD + messageBytes.size + 1 + 1 + extras.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(HEADER_BYTE.toByte())
        buffer.putInt(record.senderId)
        buffer.putInt((record.latitude * COORDINATE_SCALE).roundToInt())
        buffer.putInt((record.longitude * COORDINATE_SCALE).roundToInt())
        buffer.putShort(messageBytes.size.toShort())
        buffer.put(messageBytes)
        buffer.put(record.batteryPercent.coerceIn(0, 100).toByte())
        buffer.put(record.hopCount.coerceIn(0, MAX_HOP_COUNT).toByte())
        buffer.put(extras)
        return buffer.array()
    }

    /** Returns null for anything that is not a ResQ record we understand. */
    fun decodeSosRecord(bytes: ByteArray): SosRecord? {
        if (bytes.size < RECORD_FIXED_HEAD) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!isKnownHeader(buffer.get().toInt() and 0xFF)) return null
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
        val extras = decodeExtras(buffer)
        return SosRecord(
            senderId = senderId,
            latitude = lat,
            longitude = lon,
            message = message,
            batteryPercent = batteryPercent,
            hopCount = hopCount,
            positionTimeSeconds = extras.positionTimeSeconds,
            sosStartedSeconds = extras.sosStartedSeconds,
            sendIntervalMinutes = extras.sendIntervalMinutes,
        )
    }

    private fun isKnownHeader(header: Int): Boolean =
        header and 0xF0 == PROTOCOL_MARKER && header and 0x0F == PROTOCOL_VERSION

    private fun encodeExtras(record: SosRecord): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun put4(type: Int, seconds: Long) {
            out.write(type)
            out.write(4)
            out.write(ByteBuffer.allocate(4).putInt(seconds.coerceIn(0L, 0xFFFFFFFFL).toInt()).array())
        }
        record.positionTimeSeconds?.let { put4(RecordExtra.POSITION_TIME, it) }
        record.sosStartedSeconds?.let { put4(RecordExtra.SOS_STARTED, it) }
        record.sendIntervalMinutes?.let {
            out.write(RecordExtra.SEND_INTERVAL)
            out.write(1)
            out.write(it.coerceIn(0, 255))
        }
        return out.toByteArray()
    }

    private class Extras(
        var positionTimeSeconds: Long? = null,
        var sosStartedSeconds: Long? = null,
        var sendIntervalMinutes: Int? = null,
    )

    /**
     * Reads the `type, length, value` items after the fixed part. The rules:
     * an unknown type is skipped using its length; an item whose length runs
     * past the end stops the read but keeps what came before; a known type
     * with an unexpected length is skipped; a repeated type keeps the first.
     * Type 0 is reserved and is skipped like any other unknown type.
     */
    private fun decodeExtras(buffer: ByteBuffer): Extras {
        val extras = Extras()
        while (buffer.remaining() >= 2) {
            val type = buffer.get().toInt() and 0xFF
            val length = buffer.get().toInt() and 0xFF
            if (length > buffer.remaining()) break
            val start = buffer.position()
            when {
                type == RecordExtra.POSITION_TIME && length == 4 ->
                    if (extras.positionTimeSeconds == null) {
                        extras.positionTimeSeconds = buffer.int.toLong() and 0xFFFFFFFFL
                    }
                type == RecordExtra.SOS_STARTED && length == 4 ->
                    if (extras.sosStartedSeconds == null) {
                        extras.sosStartedSeconds = buffer.int.toLong() and 0xFFFFFFFFL
                    }
                type == RecordExtra.SEND_INTERVAL && length == 1 ->
                    if (extras.sendIntervalMinutes == null) {
                        extras.sendIntervalMinutes = buffer.get().toInt() and 0xFF
                    }
            }
            buffer.position(start + length)
        }
        return extras
    }
}
