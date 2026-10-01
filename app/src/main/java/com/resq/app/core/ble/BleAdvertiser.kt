package com.resq.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.resq.app.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "BleAdvertiser"

sealed interface AdvertiseState {
    data object Idle : AdvertiseState
    data object Advertising : AdvertiseState
    data class Failed(val reason: String) : AdvertiseState
}

/**
 * Owns a single [BluetoothLeAdvertiser] session. Only one beacon payload
 * is advertised at a time — [start] replaces whatever was previously
 * advertising, which is what we want when a new GPS fix or a new relay
 * candidate needs to go out.
 */
class BleAdvertiser(private val context: Context) {

    private val _state = MutableStateFlow<AdvertiseState>(AdvertiseState.Idle)
    val state: StateFlow<AdvertiseState> = _state.asStateFlow()

    private val advertiser: BluetoothLeAdvertiser?
        get() = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeAdvertiser

    private var callback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission") // caller verifies BleCapability.readinessToAdvertise first
    fun start(payload: BeaconPayload) {
        val leAdvertiser = advertiser
        if (leAdvertiser == null) {
            _state.value = AdvertiseState.Failed("No BLE advertiser available")
            return
        }
        stop()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid.fromString(serviceUuid16String()))
            .addServiceData(
                ParcelUuid.fromString(serviceUuid16String()),
                BlePayloadCodec.encodeBeacon(payload),
            )
            .build()

        val newCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Logger.d(TAG) { "Advertising started" }
                _state.value = AdvertiseState.Advertising
            }

            override fun onStartFailure(errorCode: Int) {
                Logger.w(TAG) { "Advertising failed: code=$errorCode" }
                _state.value = AdvertiseState.Failed(advertiseErrorToString(errorCode))
            }
        }
        callback = newCallback
        leAdvertiser.startAdvertising(settings, data, newCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        advertiser?.stopAdvertising(cb)
        callback = null
        _state.value = AdvertiseState.Idle
    }

    private fun serviceUuid16String(): String =
        String.format("0000%04x-0000-1000-8000-00805f9b34fb", BleConstants.BEACON_SERVICE_UUID_16)

    private fun advertiseErrorToString(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Payload too large"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many concurrent advertisers"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Already advertising"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal adapter error"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Advertising unsupported on this device"
        else -> "Unknown error ($code)"
    }
}
