package com.resq.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.resq.app.core.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "GattClientManager"
private const val CONNECT_TIMEOUT_MILLIS = 8_000L
private const val DESIRED_MTU = 247

/**
 * Central-side, one-shot GATT read: connect to [deviceAddress], pull the
 * [SosRecord] characteristic, disconnect. Used by the relay pipeline to
 * fetch the full message/GPS a [BeaconDetection] only hinted at.
 *
 * Every path — success, GATT error, or timeout — closes the
 * [BluetoothGatt] handle. Leaking these is a classic source of "ran out
 * of GATT connections" crashes on real devices after sustained use.
 */
class GattClientManager(private val context: Context) {

    @SuppressLint("MissingPermission") // caller verifies BleCapability.readinessToScan first
    suspend fun fetchSosRecord(deviceAddress: String): SosRecord? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val device = adapter.getRemoteDevice(deviceAddress)

        return withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                var gatt: BluetoothGatt? = null

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            // If the request is refused, go on at once instead of waiting
                            // for the timeout (the read still works, in smaller pieces).
                            if (!g.requestMtu(DESIRED_MTU)) g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (continuation.isActive) continuation.resume(null)
                            g.close()
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        g.discoverServices()
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            finish(g, continuation, null)
                            return
                        }
                        val char = g.getService(BleConstants.GATT_SERVICE_UUID)
                            ?.getCharacteristic(BleConstants.GATT_SOS_RECORD_CHARACTERISTIC_UUID)
                        if (char == null) {
                            finish(g, continuation, null)
                        } else {
                            g.readCharacteristic(char)
                        }
                    }

                    @Suppress("DEPRECATION") // pre-33 callback signature still required at minSdk 23
                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        val record = if (status == BluetoothGatt.GATT_SUCCESS) {
                            BlePayloadCodec.decodeSosRecord(characteristic.value ?: ByteArray(0))
                        } else {
                            null
                        }
                        finish(g, continuation, record)
                    }
                }

                // LE only: avoids the classic-transport attempt that causes many status 133 failures.
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                continuation.invokeOnCancellation {
                    Logger.d(TAG) { "GATT read cancelled for $deviceAddress" }
                    gatt?.disconnect()
                    gatt?.close()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun finish(
        gatt: BluetoothGatt,
        continuation: CancellableContinuation<SosRecord?>,
        result: SosRecord?,
    ) {
        if (continuation.isActive) {
            continuation.resume(result)
        }
        gatt.disconnect()
        gatt.close()
    }
}
