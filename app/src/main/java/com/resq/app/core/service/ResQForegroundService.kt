package com.resq.app.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.resq.app.core.ble.BeaconFlags
import com.resq.app.core.ble.BeaconPayload
import com.resq.app.core.ble.MAX_HOP_COUNT
import com.resq.app.core.ble.MAX_UPDATE_COUNTER
import com.resq.app.core.ble.SosRecord
import com.resq.app.core.ble.UpdateVerdict
import com.resq.app.core.data.model.SosMessage
import com.resq.app.core.data.settings.SosSession
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.ReadinessRefreshTrigger
import com.resq.app.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ResQForegroundService"
private const val RELAY_CYCLE_INTERVAL_MILLIS = 2 * 60_000L
private const val STALE_PRUNE_INTERVAL_MILLIS = 10 * 60_000L
private const val FIX_RETRY_BACKOFF_MILLIS = 30_000L

/**
 * The one long-running component in the app. It owns three independent
 * loops gated by two boolean roles (sending / receiving) that the UI
 * toggles on and off:
 *
 * - **Send loop**: fetch a GPS fix, advertise it, repeat on
 *   [BroadcastInterval]. While this is running it has exclusive use of
 *   the BLE advertiser — see the relay loop note below for why.
 * - **Scan loop**: always-on while receiving; every detection updates
 *   the local log and opportunistically pulls the full message over GATT.
 * - **Relay loop**: only actually advertises when the send loop is
 *   *not* active. A phone has one practical BLE advertising slot in
 *   this app's model (see [com.resq.app.core.ble.BleAdvertiser]), so v1
 *   prioritizes a device's own SOS over relaying someone else's — your
 *   own emergency wins the slot. Running both simultaneously via
 *   multiple concurrent advertising sets is a real option on capable
 *   hardware and is the natural v2 extension point here.
 */
class ResQForegroundService : Service() {

    private val locator by lazy { ServiceLocator.getInstance(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob())

    private var sendJob: Job? = null
    private var scanJob: Job? = null
    private var relayJob: Job? = null
    private var pruneJob: Job? = null

    // Per-SOS state, saved by SettingsRepository.beginSession so a resumed SOS can keep it.
    private var sessionSenderId = 0
    private var updateCounter = 0
    private var sosStartedAtSeconds = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_RESUME_SENDING) {
            // Either Android restarted us after killing the app (START_STICKY, no action)
            // or the app was opened while a recent SOS was never stopped. Carry it on with
            // the same ID, counter and Start time instead of silently leaving it ended.
            ensureForeground()
            scope.launch {
                val saved = locator.settingsRepository.activeSession(System.currentTimeMillis() / 1000)
                if (saved != null) {
                    startSending(resume = saved)
                    updateNotification()
                } else {
                    stopIfIdle()
                }
            }
            return START_STICKY
        }
        when (intent.action) {
            ACTION_START_SENDING -> startSending()
            ACTION_STOP_SENDING -> stopSending()
            ACTION_START_RECEIVING -> startReceiving()
            ACTION_STOP_RECEIVING -> stopReceiving()
            ACTION_STOP_ALL -> {
                stopSending()
                stopReceiving()
            }
        }
        updateNotification()
        stopIfIdle()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            locator.bleAdvertiser.stop()
            locator.bleScanner.stop()
            locator.gattServerManager.stop()
        }
        sendJob?.cancel()
        scanJob?.cancel()
        relayJob?.cancel()
        pruneJob?.cancel()
        super.onDestroy()
    }

    // --- Sending -------------------------------------------------------

    @Synchronized
    private fun startSending(resume: SosSession? = null) {
        if (sendJob?.isActive == true) return
        ensureForeground()
        locator.sessionController.setSending(true)
        locator.gattServerManager.start()

        sendJob = scope.launch {
            val session = resume ?: locator.settingsRepository.beginSession(System.currentTimeMillis() / 1000)
            sessionSenderId = session.senderId
            updateCounter = session.counter
            sosStartedAtSeconds = session.startedAtSeconds
            while (true) {
                val fixObtained = broadcastOwnFix()
                val interval = locator.settingsRepository.effectiveSendInterval()
                if (fixObtained) {
                    delay(interval.millis)
                } else {
                    // No fix this cycle. Two different failure shapes need two
                    // different retry speeds:
                    //  - Missing permission / Bluetooth or GPS off: nothing
                    //    will change until the user acts, so react instantly
                    //    to that action via ReadinessRefreshTrigger instead of
                    //    waiting out the full interval.
                    //  - GPS just hasn't locked yet (slow cold start, weak
                    //    signal indoors): nothing "changes" to trigger that —
                    //    it just needs another attempt soon. A full 15-minute
                    //    or hourly interval would make the very first fix feel
                    //    like it never arrives, so cap the wait at
                    //    FIX_RETRY_BACKOFF_MILLIS regardless, and only fall
                    //    back to the user's actual interval once a fix has
                    //    been obtained at least once.
                    withTimeoutOrNull(minOf(interval.millis, FIX_RETRY_BACKOFF_MILLIS)) {
                        ReadinessRefreshTrigger.rawChanges(applicationContext, locator).first()
                    }
                }
            }
        }
    }

    private fun stopSending() {
        sendJob?.cancel()
        sendJob = null
        locator.sessionController.setSending(false)
        scope.launch { locator.settingsRepository.endSession() }
        // Hand the advertiser back to the relay loop, if it's running.
        if (relayJob?.isActive != true) {
            locator.bleAdvertiser.stop()
        }
    }

    /** @return true if a fix was obtained and broadcast, false if this cycle produced nothing. */
    private suspend fun broadcastOwnFix(): Boolean {
        if (!PermissionManager.hasLocationPermissions(this)) {
            Logger.w(TAG) { "Location permission missing this cycle" }
            return false
        }
        val fix = locator.locationProvider.requestSingleFix()
        if (fix == null) {
            Logger.w(TAG) { "No GPS fix available this cycle" }
            return false
        }
        val senderId = sessionSenderId
        val saved = locator.settingsRepository.settings.first()
        val message = SosMessage.compose(saved.locationNote, saved.sosMessageDraft)
        val batteryPercent = locator.batteryPercent()
        locator.sessionController.recordBatteryPercent(batteryPercent)

        val flags = (if (message.isNotBlank()) BeaconFlags.HAS_FULL_MESSAGE else 0) or
            (if (batteryPercent in 0..15) BeaconFlags.BATTERY_CRITICAL else 0)

        val counter = updateCounter
        updateCounter = (updateCounter + 1).coerceAtMost(MAX_UPDATE_COUNTER)
        locator.settingsRepository.saveSessionCounter(updateCounter, System.currentTimeMillis() / 1000)

        locator.bleAdvertiser.start(
            BeaconPayload(
                senderId = senderId,
                latitude = fix.latitude,
                longitude = fix.longitude,
                flags = flags,
                hopCount = 0,
                updateCounter = counter,
            )
        )
        locator.gattServerManager.updateRecord(
            SosRecord(
                senderId = senderId,
                latitude = fix.latitude,
                longitude = fix.longitude,
                message = message,
                batteryPercent = batteryPercent,
                hopCount = 0,
                positionTimeSeconds = fix.capturedAtEpochMillis / 1000,
                sosStartedSeconds = sosStartedAtSeconds,
                sendIntervalMinutes = locator.settingsRepository.effectiveSendInterval().minutes.toInt(),
            )
        )
        locator.sessionController.recordFix(fix)
        return true
    }

    // --- Receiving / relay ----------------------------------------------

    private fun startReceiving() {
        if (scanJob?.isActive == true) return
        ensureForeground()
        locator.sessionController.setReceiving(true)
        locator.gattServerManager.start()
        locator.bleScanner.start()

        scanJob = locator.bleScanner.detections
            .onEach { detection ->
                val nowSeconds = System.currentTimeMillis() / 1000
                // Our own SOS passed back to us is not someone else's emergency.
                if (locator.settingsRepository.isOwnSenderId(detection.payload.senderId, nowSeconds)) return@onEach
                val verdict = locator.sosLogRepository.recordBeaconSighting(detection)
                // Connect only for a new sender or a newer update; the same update is not fetched again.
                if (verdict == UpdateVerdict.SAVE_AND_FETCH) {
                    locator.sosLogRepository.tryEnrichWithFullRecord(detection)
                }
            }
            .launchIn(scope)

        relayJob = scope.launch {
            while (true) {
                relayCycle()
                delay(RELAY_CYCLE_INTERVAL_MILLIS)
            }
        }

        pruneJob = scope.launch {
            while (true) {
                delay(STALE_PRUNE_INTERVAL_MILLIS)
                locator.sosLogRepository.pruneStaleEntries()
            }
        }
    }

    private fun stopReceiving() {
        scanJob?.cancel()
        relayJob?.cancel()
        pruneJob?.cancel()
        scanJob = null
        relayJob = null
        pruneJob = null
        locator.bleScanner.stop()
        locator.sessionController.setReceiving(false)
        if (sendJob?.isActive != true) {
            locator.bleAdvertiser.stop()
            locator.gattServerManager.stop()
        }
    }

    private suspend fun relayCycle() {
        if (sendJob?.isActive == true) return // own SOS keeps the advertiser slot
        val nowSeconds = System.currentTimeMillis() / 1000
        val candidate = locator.sosLogRepository.mostUrgentRelayCandidate { id ->
            locator.settingsRepository.isOwnSenderId(id, nowSeconds)
        } ?: return
        if (candidate.hopCount >= MAX_HOP_COUNT) return

        locator.bleAdvertiser.start(
            BeaconPayload(
                senderId = candidate.senderId,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                flags = BeaconFlags.HAS_FULL_MESSAGE or BeaconFlags.IS_RELAYED or
                    (if (candidate.isCancelled) BeaconFlags.CANCELLED else 0) or
                    (if (candidate.positionUnknown) BeaconFlags.POSITION_UNKNOWN else 0),
                hopCount = candidate.hopCount + 1,
                // The original sender's counter, so every phone can tell this is the same update.
                updateCounter = candidate.updateCounter,
            )
        )
        locator.gattServerManager.updateRecord(
            SosRecord(
                senderId = candidate.senderId,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                message = candidate.message,
                batteryPercent = candidate.batteryPercent,
                hopCount = candidate.hopCount + 1,
                positionTimeSeconds = candidate.positionTimeSeconds,
                sosStartedSeconds = candidate.sosStartedSeconds,
                sendIntervalMinutes = candidate.sendIntervalMinutes,
            )
        )
    }

    // --- Lifecycle plumbing ----------------------------------------------

    private fun ensureForeground() {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification())
    }

    private fun updateNotification() {
        if (sendJob?.isActive == true || scanJob?.isActive == true) {
            ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
                ?.notify(FOREGROUND_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification() = ResQNotifications.build(
        this,
        isSending = sendJob?.isActive == true,
        isReceiving = scanJob?.isActive == true,
    )

    private fun stopIfIdle() {
        if (sendJob?.isActive != true && scanJob?.isActive != true) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START_SENDING = "com.resq.app.action.START_SENDING"
        const val ACTION_STOP_SENDING = "com.resq.app.action.STOP_SENDING"
        const val ACTION_START_RECEIVING = "com.resq.app.action.START_RECEIVING"
        const val ACTION_STOP_RECEIVING = "com.resq.app.action.STOP_RECEIVING"
        const val ACTION_RESUME_SENDING = "com.resq.app.action.RESUME_SENDING"
        const val ACTION_STOP_ALL = "com.resq.app.action.STOP_ALL"

        private fun start(context: Context, action: String) {
            val intent = Intent(context, ResQForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startSending(context: Context) = start(context, ACTION_START_SENDING)
        fun resumeSending(context: Context) = start(context, ACTION_RESUME_SENDING)
        fun stopSending(context: Context) = start(context, ACTION_STOP_SENDING)
        fun startReceiving(context: Context) = start(context, ACTION_START_RECEIVING)
        fun stopReceiving(context: Context) = start(context, ACTION_STOP_RECEIVING)
    }
}
