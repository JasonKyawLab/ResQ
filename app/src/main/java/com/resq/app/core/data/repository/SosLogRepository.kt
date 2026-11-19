package com.resq.app.core.data.repository

import com.resq.app.core.ble.BeaconDetection
import com.resq.app.core.ble.GattClientManager
import com.resq.app.core.ble.SosRecord
import com.resq.app.core.data.db.SosLogDao
import com.resq.app.core.data.db.SosLogEntity
import com.resq.app.core.util.Logger
import kotlinx.coroutines.flow.Flow

private const val TAG = "SosLogRepository"

/** How long a sighting stays in the log without a fresh re-sighting before it's pruned. */
const val STALE_ENTRY_MAX_AGE_MILLIS = 60 * 60_000L // 1 hour

/**
 * Bridges raw BLE detections into the durable receiver log. A detection
 * always upserts the cheap beacon fields immediately (sender is on the
 * mesh, roughly here, right now); the fuller message is filled in
 * afterwards if/when a GATT read succeeds, so the UI never blocks on a
 * connection that might fail or take seconds.
 */
class SosLogRepository(
    private val dao: SosLogDao,
    private val gattClientManager: GattClientManager,
) {

    fun observeLog(): Flow<List<SosLogEntity>> = dao.observeAll()

    suspend fun recordBeaconSighting(detection: BeaconDetection) {
        val existing = dao.findBySenderId(detection.payload.senderId)
        dao.upsert(
            SosLogEntity(
                senderId = detection.payload.senderId,
                message = existing?.message ?: "",
                latitude = detection.payload.latitude,
                longitude = detection.payload.longitude,
                batteryPercent = existing?.batteryPercent ?: -1,
                rssi = detection.rssi,
                hopCount = detection.payload.hopCount,
                firstSeenAtEpochMillis = existing?.firstSeenAtEpochMillis ?: detection.seenAtEpochMillis,
                lastSeenAtEpochMillis = detection.seenAtEpochMillis,
                hasFullMessage = existing?.hasFullMessage ?: false,
            )
        )
    }

    /** Attempts the richer GATT pull; safe to call even if it fails or times out. */
    suspend fun tryEnrichWithFullRecord(detection: BeaconDetection) {
        if (!detection.payload.hasFullMessage) return
        val record = runCatching { gattClientManager.fetchSosRecord(detection.deviceAddress) }
            .onFailure { Logger.w(TAG, it) { "GATT enrich failed for ${detection.deviceAddress}" } }
            .getOrNull() ?: return
        mergeFullRecord(record, detection.rssi)
    }

    private suspend fun mergeFullRecord(record: SosRecord, rssi: Int) {
        val existing = dao.findBySenderId(record.senderId)
        val now = System.currentTimeMillis()
        dao.upsert(
            SosLogEntity(
                senderId = record.senderId,
                message = record.message,
                latitude = record.latitude,
                longitude = record.longitude,
                batteryPercent = record.batteryPercent,
                rssi = rssi,
                hopCount = record.hopCount,
                firstSeenAtEpochMillis = existing?.firstSeenAtEpochMillis ?: now,
                lastSeenAtEpochMillis = now,
                hasFullMessage = true,
            )
        )
    }

    suspend fun pruneStaleEntries() {
        dao.pruneStaleEntries(System.currentTimeMillis() - STALE_ENTRY_MAX_AGE_MILLIS)
    }

    /** Freshest sighting from someone else, used by the relay loop to pick what to re-advertise next. */
    suspend fun mostUrgentRelayCandidate(isOwn: suspend (Int) -> Boolean): SosLogEntity? =
        dao.getAllSnapshot().firstOrNull { !isOwn(it.senderId) }
}
