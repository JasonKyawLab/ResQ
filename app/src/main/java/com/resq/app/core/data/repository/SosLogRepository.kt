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
import com.resq.app.core.ble.FetchBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val TAG = "SosLogRepository"

/** A random wait before connecting, so a crowd of helpers does not all connect at the same moment. */
private const val FETCH_JITTER_MIN_MILLIS = 500L
private const val FETCH_JITTER_MAX_MILLIS = 3_000L

/** What recording a sighting decided, and whether the caller should now connect for the full record. */
data class SightingResult(val verdict: UpdateVerdict, val needsFetch: Boolean)

/** How long a sighting stays in the log without a fresh re-sighting before it's pruned (see EntryLifecycle). */
const val STALE_ENTRY_MAX_AGE_MILLIS = EntryLifecycle.DELETE_AFTER_MILLIS

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

    private val fetchBackoff = FetchBackoff()

    /** Senders with a connection attempt under way: one attempt at a time per sender. */
    private val fetchInFlight = ConcurrentHashMap<Int, Boolean>()

    /** One connection at a time overall: simpler on the radio and far more reliable on cheap phones. */
    private val fetchGate = Semaphore(1)

    fun observeLog(): Flow<List<SosLogEntity>> = dao.observeVisible()

    suspend fun dismiss(senderId: Int) = dao.dismiss(senderId)

    /** Removes every received SOS from this phone. One still being sent nearby shows up again when heard. */
    suspend fun clearAll() = dao.clearAll()

    /**
     * Applies the once-only rules (see [UpdateRules]) and returns what happened, so the
     * caller only connects for the full record when [UpdateVerdict.SAVE_AND_FETCH] says so.
     */
    suspend fun recordBeaconSighting(detection: BeaconDetection): SightingResult {
        val payload = detection.payload
        val existing = dao.findBySenderId(payload.senderId)
        val verdict = UpdateRules.decide(existing?.updateCounter, payload.updateCounter)
        // A newer update starts fresh: earlier failures were about an older update.
        if (verdict == UpdateVerdict.SAVE_AND_FETCH) fetchBackoff.reset(payload.senderId)
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
                    ).let {
                        // A relayed repeat of the update we hold must not restart the Live clock.
                        if (UpdateRules.refreshesLiveClock(verdict, payload.hopCount)) {
                            it.copy(
                                lastSeenAtEpochMillis = detection.seenAtEpochMillis,
                                lastSeenElapsedMillis = SystemClock.elapsedRealtime(),
                            )
                        } else {
                            it
                        }
                    }
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
        // Connect for a new or newer update, and also to retry an update whose full record never
        // arrived (the back-off in tryEnrichWithFullRecord keeps that retry gentle).
        val needsFetch = payload.hasFullMessage && when (verdict) {
            UpdateVerdict.SAVE_AND_FETCH -> true
            UpdateVerdict.REFRESH_ONLY -> existing != null && !existing.hasFullMessage
            UpdateVerdict.IGNORE -> false
        }
        return SightingResult(verdict, needsFetch)
    }

    /**
     * Attempts the richer GATT pull. Safe to call for every beacon: it does nothing if an
     * attempt for this sender is already running, if the back-off says to wait, or if the
     * record turned up in the meantime. One connection at a time, after a short random wait.
     */
    suspend fun tryEnrichWithFullRecord(detection: BeaconDetection) {
        if (!detection.payload.hasFullMessage) return
        val sender = detection.payload.senderId
        if (fetchInFlight.putIfAbsent(sender, true) != null) return
        try {
            if (!fetchBackoff.canAttempt(sender, SystemClock.elapsedRealtime())) return
            delay(Random.nextLong(FETCH_JITTER_MIN_MILLIS, FETCH_JITTER_MAX_MILLIS + 1))
            val stored = dao.findBySenderId(sender)
            // Gone, already complete, or superseded by a newer update while we waited.
            if (stored == null || stored.hasFullMessage ||
                UpdateRules.compare(detection.payload.updateCounter, stored.updateCounter) < 0
            ) return

            val record = fetchGate.withPermit {
                try {
                    gattClientManager.fetchSosRecord(detection.deviceAddress)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "GATT enrich failed for ${detection.deviceAddress}" }
                    null
                }
            }
            if (record == null || record.senderId != sender) {
                fetchBackoff.recordFailure(sender, SystemClock.elapsedRealtime())
                return
            }
            fetchBackoff.reset(sender)
            mergeFullRecord(record, detection.rssi)
        } finally {
            fetchInFlight.remove(sender)
        }
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
                // Reading the full record is not a new sign of life: the beacon that led here already
                // refreshed the clock if it should have. A sender never seen before starts it now.
                lastSeenAtEpochMillis = existing?.lastSeenAtEpochMillis ?: now,
                hasFullMessage = true,
                updateCounter = existing?.updateCounter ?: 0,
                isRelayed = existing?.isRelayed ?: false,
                isCancelled = existing?.isCancelled ?: false,
                positionUnknown = existing?.positionUnknown ?: false,
                positionTimeSeconds = record.positionTimeSeconds,
                sosStartedSeconds = record.sosStartedSeconds,
                sendIntervalMinutes = record.sendIntervalMinutes,
                lastSeenElapsedMillis = existing?.lastSeenElapsedMillis ?: SystemClock.elapsedRealtime(),
                dismissed = existing?.dismissed ?: false,
            )
        )
    }

    suspend fun pruneStaleEntries() {
        dao.pruneStaleEntries(System.currentTimeMillis() - STALE_ENTRY_MAX_AGE_MILLIS)
    }

    /** Freshest sighting from someone else, used by the relay loop to pick what to re-advertise next. */
    suspend fun mostUrgentRelayCandidate(isOwn: suspend (Int) -> Boolean): SosLogEntity? {
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        return dao.getAllSnapshot().firstOrNull { entry ->
            val age = EntryLifecycle.heardAgeMillis(
                entry.lastSeenAtEpochMillis, entry.lastSeenElapsedMillis, now, nowElapsed,
            )
            val stage = EntryLifecycle.stage(entry.isCancelled, age, entry.sendIntervalMinutes)
            !isOwn(entry.senderId) && EntryLifecycle.shouldRelay(stage, age, entry.dismissed)
        }
    }
}
