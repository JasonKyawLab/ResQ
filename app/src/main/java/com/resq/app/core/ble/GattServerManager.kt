package com.resq.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.resq.app.BuildConfig
import com.resq.app.core.util.Logger
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GattServerManager"

/** Most helpers connected at once; a further one is disconnected straight away. */
private const val MAX_HELPERS = 4

/**
 * A helper gets this long in total, however it behaves. A phone reads in well under a second, so
 * release builds use 10 seconds. Debug builds allow 2 minutes so a person can connect with a
 * Bluetooth tool (nRF Connect, LightBlue) and read the record by hand.
 */
private val MAX_CONNECTION_MILLIS = if (BuildConfig.DEBUG) 120_000L else 10_000L

/** After a read request the helper is let go this long later, unless it asks for more. */
private const val LINGER_AFTER_READ_MILLIS = 1_500L

/**
 * Peripheral-side GATT server. Holds exactly one readable characteristic
 * — the current [SosRecord] this device wants to offer — and serves it
 * to any central that connects and reads. [updateRecord] is cheap and
 * safe to call every time the active record changes (new GPS fix, new
 * relay candidate); there is nothing to restart.
 */
class GattServerManager(private val context: Context) {

    private var gattServer: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var currentRecordBytes: ByteArray = ByteArray(0)

    private val limiter = ConnectionLimiter(MAX_HELPERS)
    private val handler = Handler(Looper.getMainLooper())
    private val connectedAt = ConcurrentHashMap<String, Long>()
    private val disconnectTimers = ConcurrentHashMap<String, Runnable>()

    /** Called after any helper disconnects, so the owner can make sure the beacon is still on the air. */
    @Volatile
    var onHelperDisconnected: (() -> Unit)? = null

    @SuppressLint("MissingPermission") // caller verifies BleCapability.readinessToAdvertise first
    fun start() {
        if (gattServer != null) return
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return

        val char = BluetoothGattCharacteristic(
            BleConstants.GATT_SOS_RECORD_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        characteristic = char

        val service = BluetoothGattService(
            BleConstants.GATT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        ).apply { addCharacteristic(char) }

        val callback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                requestedCharacteristic: BluetoothGattCharacteristic,
            ) {
                val bytes = currentRecordBytes
                val value = if (offset > bytes.size) {
                    ByteArray(0)
                } else {
                    bytes.copyOfRange(offset, bytes.size)
                }
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value,
                )
                // A long read comes as several requests; each one re-arms the short timer.
                scheduleDisconnect(device, LINGER_AFTER_READ_MILLIS)
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                Logger.d(TAG) { "Central ${device.address} state=$newState" }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (!limiter.tryAdmit(device.address)) {
                            Logger.w(TAG) { "Too many helpers, dropping ${device.address}" }
                            dropHelper(device)
                            return
                        }
                        connectedAt.putIfAbsent(device.address, SystemClock.elapsedRealtime())
                        scheduleDisconnect(device, MAX_CONNECTION_MILLIS)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        limiter.release(device.address)
                        connectedAt.remove(device.address)
                        disconnectTimers.remove(device.address)?.let(handler::removeCallbacks)
                        onHelperDisconnected?.invoke()
                    }
                }
            }
        }

        gattServer = manager.openGattServer(context, callback)
        gattServer?.addService(service)
        Logger.d(TAG) { "GATT server started" }
    }

    /** Publishes a new record for any future read; does not push to already-connected centrals. */
    fun updateRecord(record: SosRecord) {
        currentRecordBytes = BlePayloadCodec.encodeSosRecord(record)
    }

    /** Lets the helper go after [delayMillis], never later than [MAX_CONNECTION_MILLIS] after it connected. */
    private fun scheduleDisconnect(device: BluetoothDevice, delayMillis: Long) {
        val started = connectedAt[device.address] ?: SystemClock.elapsedRealtime()
        val remainingBudget = (started + MAX_CONNECTION_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        disconnectTimers.remove(device.address)?.let(handler::removeCallbacks)
        val task = Runnable { dropHelper(device) }
        disconnectTimers[device.address] = task
        handler.postDelayed(task, minOf(delayMillis, remainingBudget))
    }

    @SuppressLint("MissingPermission")
    private fun dropHelper(device: BluetoothDevice) {
        gattServer?.cancelConnection(device)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        disconnectTimers.clear()
        connectedAt.clear()
        limiter.clear()
        gattServer?.close()
        gattServer = null
        characteristic = null
    }
}
