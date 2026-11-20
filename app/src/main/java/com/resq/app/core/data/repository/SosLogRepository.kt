package com.resq.app.core.data.repository

import android.os.SystemClock
import com.resq.app.core.ble.BeaconDetection
import com.resq.app.core.ble.GattClientManager
import com.resq.app.core.ble.SosRecord
import com.resq.app.core.ble.UpdateRules
import com.resq.app.core.ble.UpdateVerdict
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

    /**
     * Applies the once-only rules (see [UpdateRules]) and returns what happened, so the
     * caller only connects for the full record when [UpdateVerdict.SAVE_AND_FETCH] says so.
     */
    suspend fun recordBeaconSighting(detection: BeaconDetection): UpdateVerdict {
        val payload = detection.payload
        val existing = dao.findBySenderId(payload.senderId)
        val verdict = UpdateRules.decide(existing?.updateCounter, payload.updateCounter)
        // A new sender has no stored counter; a migrated row has the default 0, which is also a real counter.
        when (verdict) {
            UpdateVerdict.IGNORE -> Unit
            UpdateVerdict.REFRESH_ONLY -> if (existing != null) {
                dao.upsert(
                    existing.copy(
                        rssi = detection.rssi,
                        hopCount = UpdateRules.lowestHop(existing.hopCount, payload.hopCount),
                        // Heard directly at least once means Nearby.
                        isRelayed = existing.isRelayed && payload.isRelayed,
                        lastSeenAtEpochMillis = detection.seenAtEpochMillis,
                        lastSeenElapsedMillis = SystemClock.elapsedRealtime(),
                    )
                )
            }
            UpdateVerdict.SAVE_AND_FETCH -> dao.upsert(
                SosLogEntity(
                    senderId = payload.senderId,
                    // Keep the old message until the new record arrives, rather than showing a blank.
                    message = existing?.message ?: "",
                    latitude = payload.latitude,
                    longitude = payload.longitude,
                    batteryPercent = existing?.batteryPercent ?: -1,
                    rssi = detection.rssi,
                    hopCount = payload.hopCount,
                    firstSeenAtEpochMillis = existing?.firstSeenAtEpochMillis ?: detection.seenAtEpochMillis,
                    lastSeenAtEpochMillis = detection.seenAtEpochMillis,
                    hasFullMessage = false,
                    updateCounter = payload.updateCounter,
                    isRelayed = payload.isRelayed,
                    isCancelled = payload.isCancelled,
                    positionUnknown = payload.positionUnknown,
                    positionTimeSeconds = null,
                    sosStartedSeconds = existing?.sosStartedSeconds,
                    sendIntervalMinutes = existing?.sendIntervalMinutes,
                    lastSeenElapsedMillis = SystemClock.elapsedRealtime(),
                    // A newer update brings a dismissed entry back; a repeat of the same one does not.
                    dismissed = false,
                )
            )
        }
        return verdict
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
                updateCounter = existing?.updateCounter ?: 0,
                isRelayed = existing?.isRelayed ?: false,
                isCancelled = existing?.isCancelled ?: false,
                positionUnknown = existing?.positionUnknown ?: false,
                positionTimeSeconds = record.positionTimeSeconds,
                sosStartedSeconds = record.sosStartedSeconds,
                sendIntervalMinutes = record.sendIntervalMinutes,
                lastSeenElapsedMillis = SystemClock.elapsedRealtime(),
                dismissed = existing?.dismissed ?: false,
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
