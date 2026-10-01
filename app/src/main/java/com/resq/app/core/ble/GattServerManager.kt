package com.resq.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import com.resq.app.core.util.Logger

private const val TAG = "GattServerManager"

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
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                Logger.d(TAG) { "Central ${device.address} state=$newState" }
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

    @SuppressLint("MissingPermission")
    fun stop() {
        gattServer?.close()
        gattServer = null
        characteristic = null
    }
}
