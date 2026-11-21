package com.resq.app.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.resq.app.core.ble.BeaconFlags
import com.resq.app.core.ble.BeaconPayload
import com.resq.app.core.ble.BleCapability
import com.resq.app.core.ble.BleReadiness
import com.resq.app.core.ble.MAX_HOP_COUNT
import com.resq.app.core.ble.MAX_UPDATE_COUNTER
import com.resq.app.core.ble.SosRecord
import com.resq.app.core.data.model.SosMessage
import com.resq.app.core.data.settings.SosSession
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.location.GpsFix
import com.resq.app.core.location.PositionPlan
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.core.permissions.ReadinessRefreshTrigger
import com.resq.app.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ResQForegroundService"
private const val RELAY_CYCLE_INTERVAL_MILLIS = 2 * 60_000L
private const val STALE_PRUNE_INTERVAL_MILLIS = 10 * 60_000L
private const val FIX_RETRY_BACKOFF_MILLIS = 30_000L
private const val MIN_RETRY_GAP_MILLIS = 1_000L

/** How long the "stopped" beacon stays on the air after Stop. Phones relay it for a while longer. */
private const val STOP_ANNOUNCE_MILLIS = 60_000L
private const val EDIT_DEBOUNCE_MILLIS = 2_000L

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

    /** Runs for a short while after the user presses Stop, telling nearby phones the SOS ended. */
    @Volatile
    private var endingJob: Job? = null

    // Per-SOS state, saved by SettingsRepository.beginSession so a resumed SOS can keep it.
    private var sessionSenderId = 0
    private var updateCounter = 0
    private var sosStartedAtSeconds = 0L

    // An edit to the message while sending asks for an immediate update. Conflated so a
    // burst of requests is one update.
    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)
    private var lastBroadcastMessage: String? = null
    private var lastBroadcastWasUnknown = false
    private var lastBroadcastAtElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_RESUME) {
            // Either Android restarted us after killing the app (START_STICKY, no action)
            // or the app was opened while something was never stopped. Carry on with what was running:
            // an SOS keeps its ID, counter and Start time; scanning simply starts again.
            ensureForeground()
            scope.launch {
                val nowSeconds = System.currentTimeMillis() / 1000
                val savedSos = locator.settingsRepository.activeSession(nowSeconds)
                if (savedSos != null) startSending(resume = savedSos)
                if (locator.settingsRepository.scanningToResume(nowSeconds) &&
                    BleCapability.readinessToScan(applicationContext) == BleReadiness.Ready
                ) {
                    startReceiving()
                }
                updateNotification()
                stopIfIdle()
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
        endingJob?.cancel()
        super.onDestroy()
    }

    // --- Sending -------------------------------------------------------

    @Synchronized
    private fun startSending(resume: SosSession? = null) {
        if (sendJob?.isActive == true) return
        // Started again while the "stopped" beacon is still on the air: this SOS carries on.
        endingJob?.cancel()
        endingJob = null
        ensureForeground()
        locator.sessionController.setSending(true)
        startGattServer()

        sendJob = scope.launch {
            val session = resume ?: locator.settingsRepository.beginSession(System.currentTimeMillis() / 1000)
            sessionSenderId = session.senderId
            updateCounter = session.counter
            sosStartedAtSeconds = session.startedAtSeconds
            lastBroadcastMessage = null
            lastBroadcastWasUnknown = false
            lastBroadcastAtElapsed = 0L
            refreshSignal.tryReceive() // drop a request left over from an earlier SOS
            watchMessageEdits()
            var editRefresh = false
            var nextFixAt = 0L // elapsedRealtime when the next real GPS fix is due
            // Instant first broadcast: with no position known yet, helpers hear the SOS and its message
            // at once as "position unknown". The first real fix follows as the next update, so waiting
            // for GPS (up to ~40 s) never delays the first sign of the SOS.
            if (locator.sessionController.state.value.lastFix == null) {
                val saved = locator.settingsRepository.settings.first()
                broadcastUpdate(fix = null, message = SosMessage.compose(saved.locationNote, saved.sosMessageDraft))
            }
            while (true) {
                val fixObtained = broadcastOwnFix(reuseLastFix = editRefresh)
                val interval = locator.settingsRepository.effectiveSendInterval()
                // Only a real fix restarts the timer. An edit update must not push the next
                // position refresh later (with Battery saver that could be an hour).
                if (fixObtained && !editRefresh) nextFixAt = SystemClock.elapsedRealtime() + interval.millis
                editRefresh = false
                if (fixObtained) {
                    // Waits until the next fix is due, or ends early when the message was edited.
                    val wait = (nextFixAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    editRefresh = withTimeoutOrNull(wait) { refreshSignal.receive() } != null
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
                    // A message edit also ends the wait, so a change typed while there is no GPS
                    // signal still goes out at once.
                    val waitStartedAt = SystemClock.elapsedRealtime()
                    editRefresh = withTimeoutOrNull(minOf(interval.millis, FIX_RETRY_BACKOFF_MILLIS)) {
                        merge(
                            ReadinessRefreshTrigger.rawChanges(applicationContext, locator).map { false },
                            refreshSignal.receiveAsFlow().map { true },
                        ).first()
                    } == true
                    // A floor, whatever woke us: a retry loop must never run faster than this, or a
                    // misbehaving signal could spin the CPU and the log until Android kills the app.
                    val waited = SystemClock.elapsedRealtime() - waitStartedAt
                    if (waited < MIN_RETRY_GAP_MILLIS) delay(MIN_RETRY_GAP_MILLIS - waited)
                }
            }
        }
    }

    /**
     * Child of the send job, so Stop cancels it and a pending edit cannot fire an update after
     * the SOS has ended. Waits until typing pauses, then asks for one immediate update.
     */
    @OptIn(FlowPreview::class)
    private fun CoroutineScope.watchMessageEdits() {
        launch {
            locator.settingsRepository.settings
                .map { SosMessage.compose(it.locationNote, it.sosMessageDraft) }
                .distinctUntilChanged()
                .debounce(EDIT_DEBOUNCE_MILLIS)
                .collect { composed ->
                    val last = lastBroadcastMessage
                    if (last != null && composed != last) refreshSignal.trySend(Unit)
                }
        }
    }

    private fun startGattServer() {
        // After a helper leaves, make sure our beacon is still on the air.
        locator.gattServerManager.onHelperDisconnected = { locator.bleAdvertiser.refresh() }
        locator.gattServerManager.start()
    }

    private fun stopSending() {
        val wasSending = sendJob?.isActive == true
        sendJob?.cancel()
        sendJob = null
        locator.sessionController.setSending(false)
        scope.launch { locator.settingsRepository.endSession(System.currentTimeMillis() / 1000) }
        if (wasSending && lastBroadcastMessage != null) {
            // Only the Stop button (or the notification's Stop) gets here, never a kill, so an SOS that
            // died with its phone is not announced as safe. Skipped if nothing was ever broadcast.
            announceStopped()
        } else if (relayJob?.isActive != true && endingJob?.isActive != true) {
            // Hand the advertiser back to the relay loop, if it's running.
            locator.bleAdvertiser.stop()
        }
    }

    /**
     * Broadcasts the same SOS once more with the "cancelled" flag and a newer update counter, so phones
     * that heard it can show "Marked safe by sender (unverified)". The last full record keeps being served.
     */
    private fun announceStopped() {
        endingJob?.cancel()
        endingJob = scope.launch {
            val fix = locator.sessionController.state.value.lastFix
            val counter = updateCounter
            updateCounter = (updateCounter + 1).coerceAtMost(MAX_UPDATE_COUNTER)
            locator.settingsRepository.saveSessionCounter(updateCounter, System.currentTimeMillis() / 1000)
            locator.bleAdvertiser.start(
                BeaconPayload(
                    senderId = sessionSenderId,
                    latitude = fix?.latitude ?: 0.0,
                    longitude = fix?.longitude ?: 0.0,
                    flags = BeaconFlags.HAS_FULL_MESSAGE or BeaconFlags.CANCELLED or
                        (if (fix == null) BeaconFlags.POSITION_UNKNOWN else 0),
                    hopCount = 0,
                    updateCounter = counter,
                )
            )
            delay(STOP_ANNOUNCE_MILLIS)
            endingJob = null
            if (relayJob?.isActive != true) locator.bleAdvertiser.stop()
            updateNotification()
            stopIfIdle()
        }
        updateNotification()
    }

    /** @return true if a fix was obtained and broadcast, false if this cycle produced nothing. */
    private suspend fun broadcastOwnFix(reuseLastFix: Boolean = false): Boolean {
        val lastFix = locator.sessionController.state.value.lastFix
        // After a message edit the update goes out at once with the last known fix (its own
        // position time is still sent) and never waits for a new GPS fix.
        val fix: GpsFix? = when {
            reuseLastFix -> lastFix
            !PermissionManager.hasLocationPermissions(this) -> {
                Logger.w(TAG) { "Location permission missing this cycle" }
                null
            }
            else -> locator.locationProvider.requestSingleFix()
        }
        val saved = locator.settingsRepository.settings.first()
        val message = SosMessage.compose(saved.locationNote, saved.sosMessageDraft)

        if (fix == null) {
            Logger.w(TAG) { "No GPS fix available this cycle" }
            // Nothing should mean "nothing on the air": with no position at all, broadcast the message and
            // signal anyway, marked "position unknown". If an earlier position exists, it is repeated once per
            // interval with its real age, which helps a rescuer more than "unknown".
            if (lastFix == null &&
                PositionPlan.shouldBroadcastWithoutFix(
                    lastBroadcastWasUnknown,
                    lastBroadcastMessage,
                    message,
                    SystemClock.elapsedRealtime() - lastBroadcastAtElapsed,
                    locator.settingsRepository.effectiveSendInterval().millis,
                )
            ) {
                broadcastUpdate(fix = null, message = message)
            } else if (lastFix != null &&
                PositionPlan.shouldRepeatLastPosition(
                    SystemClock.elapsedRealtime() - lastBroadcastAtElapsed,
                    locator.settingsRepository.effectiveSendInterval().millis,
                )
            ) {
                // GPS was lost after a fix: repeat the last position (its own time stays, so receivers see its
                // real age) with a newer counter, as a sign of life.
                broadcastUpdate(fix = lastFix, message = message)
            }
            return false
        }
        broadcastUpdate(fix, message)
        return true
    }

    /** Puts one update on the air: a newer counter, the beacon, and the full record for helpers to read. */
    private suspend fun broadcastUpdate(fix: GpsFix?, message: String) {
        val senderId = sessionSenderId
        lastBroadcastMessage = message
        lastBroadcastWasUnknown = fix == null
        lastBroadcastAtElapsed = SystemClock.elapsedRealtime()
        val batteryPercent = locator.batteryPercent()
        locator.sessionController.recordBatteryPercent(batteryPercent)

        val flags = (if (message.isNotBlank()) BeaconFlags.HAS_FULL_MESSAGE else 0) or
            (if (batteryPercent in 0..15) BeaconFlags.BATTERY_CRITICAL else 0) or
            (if (fix == null) BeaconFlags.POSITION_UNKNOWN else 0)

        val counter = updateCounter
        updateCounter = (updateCounter + 1).coerceAtMost(MAX_UPDATE_COUNTER)
        locator.settingsRepository.saveSessionCounter(updateCounter, System.currentTimeMillis() / 1000)

        val latitude = fix?.latitude ?: 0.0
        val longitude = fix?.longitude ?: 0.0
        locator.bleAdvertiser.start(
            BeaconPayload(
                senderId = senderId,
                latitude = latitude,
                longitude = longitude,
                flags = flags,
                hopCount = 0,
                updateCounter = counter,
            )
        )
        locator.gattServerManager.updateRecord(
            SosRecord(
                senderId = senderId,
                latitude = latitude,
                longitude = longitude,
                message = message,
                batteryPercent = batteryPercent,
                hopCount = 0,
                positionTimeSeconds = fix?.let { PositionPlan.positionTimeSeconds(it.capturedAtEpochMillis) },
                sosStartedSeconds = sosStartedAtSeconds,
                sendIntervalMinutes = locator.settingsRepository.effectiveSendInterval().minutes.toInt(),
            )
        )
        if (fix != null) locator.sessionController.recordFix(fix) else locator.sessionController.recordBroadcastWithoutPosition()
    }

    // --- Receiving / relay ----------------------------------------------

    private fun startReceiving() {
        if (scanJob?.isActive == true) return
        ensureForeground()
        locator.sessionController.setReceiving(true)
        scope.launch { locator.settingsRepository.markScanning(System.currentTimeMillis() / 1000) }
        startGattServer()
        locator.bleScanner.start()

        scanJob = locator.bleScanner.detections
            .onEach { detection ->
                val nowSeconds = System.currentTimeMillis() / 1000
                // Our own SOS passed back to us is not someone else's emergency.
                if (locator.settingsRepository.isOwnSenderId(detection.payload.senderId, nowSeconds)) return@onEach
                val result = locator.sosLogRepository.recordBeaconSighting(detection)
                // Connect for a new or newer update (or to retry one that failed). Launched on its
                // own so a slow connection never holds up the next beacon.
                if (result.needsFetch) {
                    scope.launch { locator.sosLogRepository.tryEnrichWithFullRecord(detection) }
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
                // Still alive: keeps the saved "scanning is on" fresh for the resume window.
                locator.settingsRepository.markScanning(System.currentTimeMillis() / 1000)
            }
        }
    }

    private fun stopReceiving() {
        // Only a real switch-off clears it. A kill never gets here, so scanning can resume.
        scope.launch { locator.settingsRepository.clearScanning() }
        scanJob?.cancel()
        relayJob?.cancel()
        pruneJob?.cancel()
        scanJob = null
        relayJob = null
        pruneJob = null
        locator.bleScanner.stop()
        locator.sessionController.setReceiving(false)
        if (sendJob?.isActive != true && endingJob?.isActive != true) {
            locator.bleAdvertiser.stop()
            locator.gattServerManager.stop()
        }
    }

    private suspend fun relayCycle() {
        if (sendJob?.isActive == true) return // own SOS keeps the advertiser slot
        if (endingJob?.isActive == true) return // the "stopped" beacon keeps it for a minute
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
        if (sendJob?.isActive == true || scanJob?.isActive == true || endingJob?.isActive == true) {
            ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
                ?.notify(FOREGROUND_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification() = ResQNotifications.build(
        this,
        isSending = sendJob?.isActive == true,
        isReceiving = scanJob?.isActive == true,
        isEnding = endingJob?.isActive == true,
    )

    private fun stopIfIdle() {
        if (sendJob?.isActive != true && scanJob?.isActive != true && endingJob?.isActive != true) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START_SENDING = "com.resq.app.action.START_SENDING"
        const val ACTION_STOP_SENDING = "com.resq.app.action.STOP_SENDING"
        const val ACTION_START_RECEIVING = "com.resq.app.action.START_RECEIVING"
        const val ACTION_STOP_RECEIVING = "com.resq.app.action.STOP_RECEIVING"
        const val ACTION_RESUME = "com.resq.app.action.RESUME"
        const val ACTION_STOP_ALL = "com.resq.app.action.STOP_ALL"

        private fun start(context: Context, action: String) {
            val intent = Intent(context, ResQForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startSending(context: Context) = start(context, ACTION_START_SENDING)
        /** Brings back whatever was running (an SOS, scanning, or both) if it was never stopped. */
        fun resumeSaved(context: Context) = start(context, ACTION_RESUME)
        fun stopSending(context: Context) = start(context, ACTION_STOP_SENDING)
        fun startReceiving(context: Context) = start(context, ACTION_START_RECEIVING)
        fun stopReceiving(context: Context) = start(context, ACTION_STOP_RECEIVING)
    }
}
