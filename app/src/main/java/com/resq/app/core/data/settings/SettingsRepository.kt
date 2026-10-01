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
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore(name = "resq_settings")

/** Snapshot of every user-configurable + device-identity setting ResQ persists. */
data class ResQSettings(
    val senderId: Int,
    val sendInterval: BroadcastInterval,
    val batterySaverEnabled: Boolean,
    val sosMessageDraft: String,
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
    }

    val settings: Flow<ResQSettings> = context.dataStore.data.map { prefs ->
        ResQSettings(
            senderId = prefs[Keys.SENDER_ID] ?: 0,
            sendInterval = BroadcastInterval.fromMinutes(
                prefs[Keys.SEND_INTERVAL_MINUTES] ?: BroadcastInterval.DEFAULT.minutes
            ),
            batterySaverEnabled = prefs[Keys.BATTERY_SAVER_ENABLED] ?: false,
            sosMessageDraft = prefs[Keys.SOS_MESSAGE_DRAFT] ?: "",
        )
    }

    /** Stable per-install identifier, generated once on first access and reused forever after. */
    suspend fun senderIdOrCreate(): Int {
        val existing = context.dataStore.data.map { it[Keys.SENDER_ID] }.first()
        if (existing != null && existing != 0) return existing
        val generated = Random.nextInt(from = 1, until = Int.MAX_VALUE)
        context.dataStore.edit { it[Keys.SENDER_ID] = generated }
        return generated
    }

    suspend fun setSendInterval(interval: BroadcastInterval) {
        context.dataStore.edit { it[Keys.SEND_INTERVAL_MINUTES] = interval.minutes }
    }

    suspend fun setBatterySaverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER_ENABLED] = enabled }
    }

    suspend fun setSosMessageDraft(message: String) {
        context.dataStore.edit { it[Keys.SOS_MESSAGE_DRAFT] = message }
    }

    /** Battery saver forces the longest interval regardless of the user's own interval pick. */
    suspend fun effectiveSendInterval(): BroadcastInterval {
        val current = settings.first()
        return if (current.batterySaverEnabled) BroadcastInterval.BATTERY_SAVER else current.sendInterval
    }
}
