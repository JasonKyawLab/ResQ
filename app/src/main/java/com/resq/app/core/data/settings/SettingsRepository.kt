package com.resq.app.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resq.app.core.data.model.BroadcastInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "resq_settings")

/** Snapshot of every user-configurable + device-identity setting ResQ persists. */
data class ResQSettings(
    val senderId: Int,
    val sendInterval: BroadcastInterval,
    val batterySaverEnabled: Boolean,
    val sosMessageDraft: String,
    /** Saved "where am I" line (room / floor / building), sent ahead of every SOS message. */
    val locationNote: String,
    /** Saved SOS state, shown on the debug line in Settings. */
    val sessionActive: Boolean = false,
    val sessionCounter: Int = 0,
    val sessionUpdatedSeconds: Long = 0L,
    /** Saved "scanning is on" state, so it can come back after Android kills the app. */
    val scanActive: Boolean = false,
    val scanUpdatedSeconds: Long = 0L,
)

/**
 * Settings live in DataStore, not Room: this is small, loosely-typed
 * key/value state (intervals, toggles, a draft string, an identity),
 * exactly what DataStore is for — Room is reserved for the structured,
 * queryable SOS log in [com.resq.app.core.data.db].
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SENDER_ID = intPreferencesKey("sender_id")
        val SEND_INTERVAL_MINUTES = longPreferencesKey("send_interval_minutes")
        val BATTERY_SAVER_ENABLED = booleanPreferencesKey("battery_saver_enabled")
        val SOS_MESSAGE_DRAFT = stringPreferencesKey("sos_message_draft")
        val LOCATION_NOTE = stringPreferencesKey("location_note")
        val SESSION_STARTED = longPreferencesKey("session_started_seconds")
        val SESSION_COUNTER = intPreferencesKey("session_counter")
        val SESSION_UPDATED = longPreferencesKey("session_updated_seconds")
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val SCAN_ACTIVE = booleanPreferencesKey("scan_active")
        val SCAN_UPDATED = longPreferencesKey("scan_updated_seconds")
        val RECENT_OWN_IDS = stringPreferencesKey("recent_own_ids")
    }

    val settings: Flow<ResQSettings> = context.dataStore.data.map { prefs ->
        ResQSettings(
            senderId = prefs[Keys.SENDER_ID] ?: 0,
            sendInterval = BroadcastInterval.fromMinutes(
                prefs[Keys.SEND_INTERVAL_MINUTES] ?: BroadcastInterval.DEFAULT.minutes
            ),
            batterySaverEnabled = prefs[Keys.BATTERY_SAVER_ENABLED] ?: false,
            sosMessageDraft = prefs[Keys.SOS_MESSAGE_DRAFT] ?: "",
            locationNote = prefs[Keys.LOCATION_NOTE] ?: "",
            sessionActive = prefs[Keys.SESSION_ACTIVE] ?: false,
            sessionCounter = prefs[Keys.SESSION_COUNTER] ?: 0,
            sessionUpdatedSeconds = prefs[Keys.SESSION_UPDATED] ?: 0L,
            scanActive = prefs[Keys.SCAN_ACTIVE] ?: false,
            scanUpdatedSeconds = prefs[Keys.SCAN_UPDATED] ?: 0L,
        )
    }

    /**
     * Starts a new SOS: a fresh random sender ID, counter back at 0, Start time now. The ID is
     * remembered for [OWN_ID_MEMORY_SECONDS] so our own SOS echoed back is not shown as someone else's.
     */
    suspend fun beginSession(nowSeconds: Long): SosSession {
        val before = context.dataStore.data.first()
        val previousId = before[Keys.SENDER_ID] ?: 0
        if (previousId != 0 && SessionResume.canContinue(before[Keys.SESSION_UPDATED] ?: 0L, nowSeconds)) {
            // Start pressed again soon after Stop: the same SOS carries on, not a second one.
            val started = before[Keys.SESSION_STARTED] ?: nowSeconds
            val counter = before[Keys.SESSION_COUNTER] ?: 0
            context.dataStore.edit {
                it[Keys.SESSION_ACTIVE] = true
                it[Keys.SESSION_UPDATED] = nowSeconds
                it[Keys.RECENT_OWN_IDS] = OwnSessionIds.remember(it[Keys.RECENT_OWN_IDS], previousId, nowSeconds)
            }
            return SosSession(previousId, started, counter, nowSeconds)
        }
        val id = OwnSessionIds.newSenderId()
        context.dataStore.edit {
            it[Keys.SENDER_ID] = id
            it[Keys.SESSION_STARTED] = nowSeconds
            it[Keys.SESSION_COUNTER] = 0
            it[Keys.SESSION_UPDATED] = nowSeconds
            it[Keys.SESSION_ACTIVE] = true
            it[Keys.RECENT_OWN_IDS] = OwnSessionIds.remember(it[Keys.RECENT_OWN_IDS], id, nowSeconds)
        }
        return SosSession(id, nowSeconds, 0)
    }

    /** The SOS that was running when the app was last stopped without pressing Stop, or null. */
    suspend fun activeSession(nowSeconds: Long): SosSession? {
        val prefs = context.dataStore.data.first()
        val active = prefs[Keys.SESSION_ACTIVE] == true
        val updated = prefs[Keys.SESSION_UPDATED] ?: 0L
        if (!SessionResume.isResumable(active, updated, nowSeconds)) {
            // Too old to resume: clear the flag so it never wakes up later by surprise.
            if (active) endSession()
            return null
        }
        val id = prefs[Keys.SENDER_ID] ?: return null
        if (id == 0) return null
        return SosSession(id, prefs[Keys.SESSION_STARTED] ?: 0L, prefs[Keys.SESSION_COUNTER] ?: 0, updated)
    }

    suspend fun saveSessionCounter(counter: Int, nowSeconds: Long) {
        context.dataStore.edit {
            it[Keys.SESSION_COUNTER] = counter
            it[Keys.SESSION_UPDATED] = nowSeconds
        }
    }

    /** The user pressed Stop: nothing to resume. The ID stays remembered as ours. */
    suspend fun endSession(stoppedAtSeconds: Long? = null) {
        context.dataStore.edit {
            it[Keys.SESSION_ACTIVE] = false
            // The Stop time starts the 30 minute window in which Start continues this SOS.
            // Cleaning up an expired session passes null and leaves the time alone.
            if (stoppedAtSeconds != null) it[Keys.SESSION_UPDATED] = stoppedAtSeconds
        }
    }

    /** Scanning was switched on (or is still running): remember it, and when it was last known alive. */
    suspend fun markScanning(nowSeconds: Long) {
        context.dataStore.edit {
            it[Keys.SCAN_ACTIVE] = true
            it[Keys.SCAN_UPDATED] = nowSeconds
        }
    }

    /** The user switched scanning off: nothing to resume. */
    suspend fun clearScanning() {
        context.dataStore.edit { it[Keys.SCAN_ACTIVE] = false }
    }

    /**
     * True if scanning was on and the app was last known running within [RESUME_WINDOW_SECONDS]. An old
     * forgotten flag is cleared, so a phone is never put back to scanning by surprise days later.
     */
    suspend fun scanningToResume(nowSeconds: Long): Boolean {
        val prefs = context.dataStore.data.first()
        val active = prefs[Keys.SCAN_ACTIVE] == true
        if (SessionResume.isResumable(active, prefs[Keys.SCAN_UPDATED] ?: 0L, nowSeconds)) return true
        if (active) clearScanning()
        return false
    }

    /** True for the running SOS's ID and any of our IDs from the last 72 hours. */
    suspend fun isOwnSenderId(id: Int, nowSeconds: Long): Boolean =
        OwnSessionIds.isOwn(context.dataStore.data.first()[Keys.RECENT_OWN_IDS], id, nowSeconds)

    suspend fun setSendInterval(interval: BroadcastInterval) {
        context.dataStore.edit { it[Keys.SEND_INTERVAL_MINUTES] = interval.minutes }
    }

    suspend fun setBatterySaverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER_ENABLED] = enabled }
    }

    suspend fun setSosMessageDraft(message: String) {
        context.dataStore.edit { it[Keys.SOS_MESSAGE_DRAFT] = message }
    }

    suspend fun setLocationNote(note: String) {
        context.dataStore.edit { it[Keys.LOCATION_NOTE] = note }
    }

    /** Battery saver forces the longest interval regardless of the user's own interval pick. */
    suspend fun effectiveSendInterval(): BroadcastInterval {
        val current = settings.first()
        return if (current.batterySaverEnabled) BroadcastInterval.BATTERY_SAVER else current.sendInterval
    }
}
