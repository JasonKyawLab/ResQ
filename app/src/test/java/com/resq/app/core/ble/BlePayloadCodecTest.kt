package com.resq.app.core.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire format down. The first two tests encode the exact examples
 * printed in ROADMAP.md, so the documentation and the code cannot drift apart.
 */
class BlePayloadCodecTest {

    private fun hex(text: String): ByteArray =
        text.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

    private val sampleRecord = SosRecord(
        senderId = 0x00ED2899,
        latitude = 21.97781,
        longitude = 96.06888,
        message = "Rm 305, 3F | Trapped. Injured",
        batteryPercent = 39,
        hopCount = 0,
        positionTimeSeconds = 0x6AFC22A1L,
        sosStartedSeconds = 0x6AFC1A60L,
        sendIntervalMinutes = 5,
    )

    // --- The documented examples -------------------------------------------------

    @Test
    fun beaconMatchesTheExampleInTheRoadmap() {
        val beacon = BeaconPayload(
            senderId = 0x00ED2899,
            latitude = 21.97781,
            longitude = 96.06888,
            flags = BeaconFlags.HAS_FULL_MESSAGE,
            hopCount = 0,
            updateCounter = 42,
        )
        assertEquals(
            "A1 00 ED 28 99 00 21 89 15 00 92 96 E8 01 00 00 2A",
            BlePayloadCodec.encodeBeacon(beacon).toHex(),
        )
    }

    @Test
    fun recordMatchesTheExampleInTheRoadmap() {
        val bytes = BlePayloadCodec.encodeSosRecord(sampleRecord)
        assertEquals(
            "A1 00 ED 28 99 00 21 89 15 00 92 96 E8 00 1D " +
                "52 6D 20 33 30 35 2C 20 33 46 20 7C 20 54 72 61 70 70 65 64 2E 20 49 6E 6A 75 72 65 64 " +
                "27 00 01 04 6A FC 22 A1 02 04 6A FC 1A 60 03 01 05",
            bytes.toHex(),
        )
    }

    // --- Beacon ---------------------------------------------------------------

    @Test
    fun beaconRoundTripsWithSouthernWesternCoordinatesAndMaxCounter() {
        val beacon = BeaconPayload(
            senderId = -123456,
            latitude = -33.86882,
            longitude = -151.20929,
            flags = BeaconFlags.IS_RELAYED or BeaconFlags.BATTERY_CRITICAL,
            hopCount = 3,
            updateCounter = MAX_UPDATE_COUNTER,
        )
        val decoded = BlePayloadCodec.decodeBeacon(BlePayloadCodec.encodeBeacon(beacon))
        assertEquals(beacon, decoded)
    }

    @Test
    fun beaconIsSeventeenBytes() {
        val beacon = BeaconPayload(1, 0.0, 0.0, 0, 0, 0)
        assertEquals(17, BlePayloadCodec.encodeBeacon(beacon).size)
    }

    @Test
    fun beaconWithAWrongMarkerIsDropped() {
        val bytes = BlePayloadCodec.encodeBeacon(BeaconPayload(1, 1.0, 1.0, 0, 0, 0))
        bytes[0] = 0x01 // the old plain version byte: no marker
        assertNull(BlePayloadCodec.decodeBeacon(bytes))
        bytes[0] = 0xB1.toByte() // some other marker
        assertNull(BlePayloadCodec.decodeBeacon(bytes))
    }

    @Test
    fun beaconWithAnUnknownVersionIsIgnoredNotACrash() {
        val bytes = BlePayloadCodec.encodeBeacon(BeaconPayload(1, 1.0, 1.0, 0, 0, 0))
        bytes[0] = 0xA2.toByte()
        assertNull(BlePayloadCodec.decodeBeacon(bytes))
    }

    @Test
    fun beaconWithUnknownFlagBitsStillDecodes() {
        val bytes = BlePayloadCodec.encodeBeacon(
            BeaconPayload(1, 1.0, 1.0, BeaconFlags.HAS_FULL_MESSAGE, 0, 0)
        )
        bytes[13] = (0xE0 or BeaconFlags.HAS_FULL_MESSAGE).toByte() // reserved bits 32, 64, 128
        val decoded = BlePayloadCodec.decodeBeacon(bytes)
        assertNotNull(decoded)
        assertTrue(decoded!!.hasFullMessage)
        assertFalse(decoded.isRelayed)
        assertFalse(decoded.isCancelled)
    }

    @Test
    fun reservedFlagBitsAreWrittenAsZero() {
        val bytes = BlePayloadCodec.encodeBeacon(BeaconPayload(1, 1.0, 1.0, 0xFF, 0, 0))
        assertEquals(BeaconFlags.KNOWN_MASK, bytes[13].toInt() and 0xFF)
    }

    @Test
    fun aLongerBeaconFromALaterVersionStillDecodes() {
        val bytes = BlePayloadCodec.encodeBeacon(BeaconPayload(7, 1.0, 2.0, 0, 1, 9)) + byteArrayOf(1, 2, 3)
        val decoded = BlePayloadCodec.decodeBeacon(bytes)
        assertNotNull(decoded)
        assertEquals(9, decoded!!.updateCounter)
    }

    @Test
    fun aShortBeaconIsRejected() {
        assertNull(BlePayloadCodec.decodeBeacon(ByteArray(16) { 0xA1.toByte() }))
    }

    @Test
    fun cancelledAndPositionUnknownFlagsAreReadable() {
        val beacon = BeaconPayload(
            1, 0.0, 0.0, BeaconFlags.CANCELLED or BeaconFlags.POSITION_UNKNOWN, 0, 5,
        )
        val decoded = BlePayloadCodec.decodeBeacon(BlePayloadCodec.encodeBeacon(beacon))!!
        assertTrue(decoded.isCancelled)
        assertTrue(decoded.positionUnknown)
    }

    @Test
    fun hopCountIsCapped() {
        val bytes = BlePayloadCodec.encodeBeacon(BeaconPayload(1, 1.0, 1.0, 0, 99, 0))
        assertEquals(MAX_HOP_COUNT, BlePayloadCodec.decodeBeacon(bytes)!!.hopCount)
    }

    // --- Record ---------------------------------------------------------------

    @Test
    fun recordRoundTripsWithExtras() {
        val decoded = BlePayloadCodec.decodeSosRecord(BlePayloadCodec.encodeSosRecord(sampleRecord))
        assertEquals(sampleRecord, decoded)
    }

    @Test
    fun anOldRecordWithoutExtrasDecodesWithNullExtras() {
        val old = sampleRecord.copy(
            positionTimeSeconds = null,
            sosStartedSeconds = null,
            sendIntervalMinutes = null,
        )
        val decoded = BlePayloadCodec.decodeSosRecord(BlePayloadCodec.encodeSosRecord(old))!!
        assertEquals(old, decoded)
        assertNull(decoded.positionTimeSeconds)
    }

    @Test
    fun anUnknownExtraTypeIsSkippedAndTheRestIsStillRead() {
        val core = BlePayloadCodec.encodeSosRecord(
            sampleRecord.copy(positionTimeSeconds = null, sosStartedSeconds = null, sendIntervalMinutes = null)
        )
        val unknown = hex("7F 03 AA BB CC")
        val known = hex("03 01 05")
        val decoded = BlePayloadCodec.decodeSosRecord(core + unknown + known)!!
        assertEquals(5, decoded.sendIntervalMinutes)
        assertEquals(sampleRecord.message, decoded.message)
    }

    @Test
    fun theFirstCopyOfARepeatedExtraWins() {
        val core = BlePayloadCodec.encodeSosRecord(
            sampleRecord.copy(positionTimeSeconds = null, sosStartedSeconds = null, sendIntervalMinutes = null)
        )
        val decoded = BlePayloadCodec.decodeSosRecord(core + hex("03 01 05 03 01 0A"))!!
        assertEquals(5, decoded.sendIntervalMinutes)
    }

    @Test
    fun anExtraThatRunsPastTheEndStopsTheReadButKeepsEarlierExtras() {
        val core = BlePayloadCodec.encodeSosRecord(
            sampleRecord.copy(positionTimeSeconds = null, sosStartedSeconds = null, sendIntervalMinutes = null)
        )
        // A good interval extra, then a position-time extra that claims 4 bytes but has 2.
        val decoded = BlePayloadCodec.decodeSosRecord(core + hex("03 01 05 01 04 6A FC"))!!
        assertEquals(5, decoded.sendIntervalMinutes)
        assertNull(decoded.positionTimeSeconds)
    }

    @Test
    fun typeZeroIsReservedAndSkipped() {
        val core = BlePayloadCodec.encodeSosRecord(
            sampleRecord.copy(positionTimeSeconds = null, sosStartedSeconds = null, sendIntervalMinutes = null)
        )
        val decoded = BlePayloadCodec.decodeSosRecord(core + hex("00 02 FF FF 03 01 07"))!!
        assertEquals(7, decoded.sendIntervalMinutes)
    }

    @Test
    fun aKnownTypeWithTheWrongLengthIsSkipped() {
        val core = BlePayloadCodec.encodeSosRecord(
            sampleRecord.copy(positionTimeSeconds = null, sosStartedSeconds = null, sendIntervalMinutes = null)
        )
        val decoded = BlePayloadCodec.decodeSosRecord(core + hex("01 03 01 02 03 03 01 09"))!!
        assertNull(decoded.positionTimeSeconds)
        assertEquals(9, decoded.sendIntervalMinutes)
    }

    @Test
    fun aRecordWithAWrongMarkerOrUnknownVersionIsRejected() {
        val bytes = BlePayloadCodec.encodeSosRecord(sampleRecord)
        bytes[0] = 0x01
        assertNull(BlePayloadCodec.decodeSosRecord(bytes))
        bytes[0] = 0xA2.toByte()
        assertNull(BlePayloadCodec.decodeSosRecord(bytes))
    }

    @Test
    fun aRecordWhoseMessageLengthLiesIsRejected() {
        val bytes = BlePayloadCodec.encodeSosRecord(sampleRecord)
        bytes[13] = 0x00
        bytes[14] = 0xFF.toByte() // claims 255 message bytes
        assertNull(BlePayloadCodec.decodeSosRecord(bytes))
    }

    @Test
    fun aFullMessageWithEmojiNeverSplitsACharacter() {
        val long = "🚨".repeat(60) // 4 bytes each = 240 bytes, over the limit
        val record = sampleRecord.copy(message = long)
        val decoded = BlePayloadCodec.decodeSosRecord(BlePayloadCodec.encodeSosRecord(record))!!
        assertEquals(45, decoded.message.codePointCount(0, decoded.message.length)) // 45 x 4 = 180
        assertTrue(decoded.message.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES)
        assertEquals("🚨".repeat(45), decoded.message)
    }

    @Test
    fun theLargestRecordFitsInOneGattPacket() {
        val record = sampleRecord.copy(message = "a".repeat(MAX_MESSAGE_BYTES))
        val size = BlePayloadCodec.encodeSosRecord(record).size
        assertEquals(212, size)
        assertTrue("one packet carries 244", size <= 244)
    }

    @Test
    fun southernWesternRecordCoordinatesRoundTrip() {
        val record = sampleRecord.copy(latitude = -33.86882, longitude = -151.20929)
        val decoded = BlePayloadCodec.decodeSosRecord(BlePayloadCodec.encodeSosRecord(record))!!
        assertEquals(-33.86882, decoded.latitude, 0.000001)
        assertEquals(-151.20929, decoded.longitude, 0.000001)
    }

    // --- truncateUtf8 ----------------------------------------------------------

    @Test
    fun truncateNeverCutsInTheMiddleOfAThreeByteCharacter() {
        val burmese = "မ".repeat(10) // 3 bytes each
        assertEquals("မ".repeat(3), burmese.truncateUtf8(10))
        assertEquals("", burmese.truncateUtf8(2))
    }

    @Test
    fun truncateLeavesShortTextAlone() {
        assertEquals("Trapped", "Trapped".truncateUtf8(180))
        assertArrayEquals(ByteArray(0), "".truncateUtf8(5).toByteArray())
    }
}
