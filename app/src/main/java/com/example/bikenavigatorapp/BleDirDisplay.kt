package com.example.bikenavigatorapp

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import java.util.*

class BleDirDisplay(private val context: MainActivity) {
    private companion object {
        const val TAG = "BleDirDisplay"
        const val SCAN_PERIOD: Long = 2000;
        const val DEVICE_ADDRESS = "24:A1:60:7F:1F:CE";
        const val DISPLAY_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
        const val DISPLAY_CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
    }

    enum class Dir {
        NO_DIR,
        RIGHT,
        STRAIGHT,
        LEFT
    }

    private val bluetoothManager by lazy { getSystemService(context, BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager!!.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private var scanning = false
    private val handler = Handler()

    var bluetoothGatt: BluetoothGatt? = null
    var displayCharacteristic: BluetoothGattCharacteristic? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")

                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            displayCharacteristic = findCharacteristic(gatt).apply {
                if (this != null) {
                    Log.i(TAG, "Found characteristic $this")
                } else {
                    Log.e(TAG, "Characteristic was not found")
                }
            };

        }
    }


    fun initiateScan() {
        if (!context.hasLocationPermissions() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.requestLocationPermissions()
        }
        scanLeDevice { res ->
            bluetoothGatt = res.device.connectGatt(context, false, bluetoothGattCallback)
        }
    }

    private fun scanLeDevice(ifFoundCb: (ScanResult) -> Unit) {
        var found: ScanResult? = null

        val leScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                Log.d(TAG, "New scan result device: $result");
                if (result.device.address == DEVICE_ADDRESS) {
                    found = result
                    Log.i(TAG, "Found device: $result");
                }
            }
        }

        bluetoothLeScanner.let { scanner ->
            if (!scanning) {
                handler.postDelayed({
                    Log.i(TAG, "Stopping scan (handler)");
                    scanning = false
                    scanner.stopScan(leScanCallback)
                    found?.let {
                        Log.i(TAG, "Found $found");
                        ifFoundCb(it);
                    }
                }, SCAN_PERIOD)
                Log.i(TAG, "Starting scan for ${SCAN_PERIOD} ms");
                scanning = true
                scanner.startScan(leScanCallback)
            }
        }
    }


    private fun findCharacteristic(gatt: BluetoothGatt?): BluetoothGattCharacteristic? {
        val found = gatt!!.services.let {
            it.filter { service ->
                service.also {
                    Log.d(
                        TAG,
                        "Service in gatt discovered: ${service.uuid}"
                    )
                }.uuid == UUID.fromString(DISPLAY_SERVICE_UUID);
            }
        }.flatMap {
            it.characteristics
        }.let {
            it.filter { characteristic ->
                characteristic.also {
                    Log.d(
                        TAG,
                        "Characteristic in gatt discovered: ${characteristic.uuid}"
                    )
                }.uuid == UUID.fromString(DISPLAY_CHARACTERISTIC_UUID)
            }
        }
        return found.elementAtOrNull(0);
    }

    private fun isBtDeviceReadyForAccess(): Boolean {
        return displayCharacteristic != null // && bluetoothManager?.getConnectionState(bluetoothGatt?.device) == BluetoothProfile.STATE_CONNECTED
    }

    fun writeDir(dir: Dir) {
        if (!isBtDeviceReadyForAccess()) {
            Log.w(TAG, "Attempting to access device which is not ready")
            return
        }
        bluetoothGatt?.let { gatt ->
            displayCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            displayCharacteristic?.value = ByteArray(1) { dir.ordinal.toByte() }
            gatt.writeCharacteristic(displayCharacteristic)
        } ?: Log.e(TAG, "Unable to write straight")
    }

    fun straight() {
        writeDir(Dir.STRAIGHT)
    }

    fun left() {
        writeDir(Dir.LEFT)

    }

    fun right() {
        writeDir(Dir.RIGHT)
    }

    fun noDir() {
        writeDir(Dir.NO_DIR)
    }

    fun isBtEnabled(): Boolean = bluetoothAdapter.isEnabled
}