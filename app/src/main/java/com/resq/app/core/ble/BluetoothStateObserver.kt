package com.resq.app.core.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits whenever the system Bluetooth adapter's on/off state changes —
 * from our own "Turn on" button, the Quick Settings tile, or the
 * system Bluetooth settings page, all of which fire the same OS
 * broadcast. Without this, toggling Bluetooth anywhere other than this
 * app's own UI never reaches the screens showing "Bluetooth is off",
 * since that status is a synchronous read, not something any of our
 * own Flows re-emit on by themselves.
 */
object BluetoothStateObserver {

    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
