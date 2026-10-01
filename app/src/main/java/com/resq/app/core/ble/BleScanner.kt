package com.resq.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.resq.app.core.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TAG = "BleScanner"

/** One beacon sighting: the decoded payload plus link-quality metadata from the OS. */
data class BeaconDetection(
    val payload: BeaconPayload,
    val deviceAddress: String,
    val rssi: Int,
    val seenAtEpochMillis: Long,
)

/**
 * Owns a single BLE scan session filtered to [BleConstants.BEACON_SERVICE_UUID_16].
 * Detections are pushed onto [detections]; callers (the foreground
 * service, the receiver log repository) decide what to do with them —
 * this class only knows how to talk to the radio.
 */
class BleScanner(private val context: Context) {

    private val _detections = MutableSharedFlow<BeaconDetection>(extraBufferCapacity = 64)
    val detections: SharedFlow<BeaconDetection> = _detections.asSharedFlow()

    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission") // caller verifies BleCapability.readinessToScan first
    fun start() {
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeScanner ?: run {
            Logger.w(TAG) { "No BLE scanner available" }
            return
        }
        stop()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(serviceUuid16String()))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleResult)
            }

            override fun onScanFailed(errorCode: Int) {
                Logger.w(TAG) { "Scan failed: code=$errorCode" }
            }
        }
        scanCallback = callback
        scanner.startScan(listOf(filter), settings, callback)
        Logger.d(TAG) { "Scan started" }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val callback = scanCallback ?: return
        context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeScanner?.stopScan(callback)
        scanCallback = null
    }

    private fun handleResult(result: ScanResult) {
        val serviceData = result.scanRecord
            ?.getServiceData(ParcelUuid.fromString(serviceUuid16String()))
            ?: return
        val payload = BlePayloadCodec.decodeBeacon(serviceData) ?: return
        _detections.tryEmit(
            BeaconDetection(
                payload = payload,
                deviceAddress = result.device.address,
                rssi = result.rssi,
                seenAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun serviceUuid16String(): String =
        String.format("0000%04x-0000-1000-8000-00805f9b34fb", BleConstants.BEACON_SERVICE_UUID_16)
}
