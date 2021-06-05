package com.example.bikenavigatorapp

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import java.util.*

class BleDirDisplay(private val context: Context) {
    companion object {
        const val TAG = "BleDirDisplay"
        const val SCAN_PERIOD: Long = 500;
        const val DEVICE_ADDRESS = "24:A1:60:7F:1F:CE";
        const val DISPLAY_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
        const val DISPLAY_CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
        const val GATT_STATE_SCANNING = 4;
        const val GATT_STATE_SCAN_SUCCESS = 5;
        const val GATT_STATE_SCAN_FAIL = 6;
    }

    data class DirData(val dir: Dir, val meters: Int)

    enum class Dir {
        NO_DIR,
        TURN_SHARP_LEFT,
        UTURN_RIGHT,
        TURN_SLIGHT_RIGHT,
        MERGE,
        ROUNDABOUT_LEFT,
        ROUNDABOUT_RIGHT,
        UTURN_LEFT,
        TURN_SLIGHT_LEFT,
        TURN_LEFT,
        RAMP_RIGHT,
        TURN_RIGHT,
        FORK_RIGHT,
        STRAIGHT,
        FORK_LEFT,
        FERRY_TRAIN,
        TURN_SHARP_RIGHT,
        RAMP_LEFT,
        FERRY
    }

    private val bluetoothManager by lazy { getSystemService(context, BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager!!.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private var scanning = false
    private val handler = Handler()

    var bluetoothGatt: BluetoothGatt? = null
    var displayCharacteristic: BluetoothGattCharacteristic? = null

    var targetDirData: DirData = DirData(Dir.NO_DIR, 0)
        set(value) {
            if (value != field) {
                isTargetWritten = false
            }
            field = value
        }
    var isTargetWritten = true

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")

                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> Log.i(TAG, "Disconnected from GATT server.")
                BluetoothProfile.STATE_CONNECTING -> Log.i(TAG, "Disconnected from GATT server.")
                BluetoothProfile.STATE_DISCONNECTING -> Log.i(TAG, "Disconnected from GATT server.")
            }
            sendUpdateGattStateBroadcast(newState)
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
                        sendUpdateGattStateBroadcast(GATT_STATE_SCAN_SUCCESS)
                        ifFoundCb(it);
                    } ?: run {
                        sendUpdateGattStateBroadcast(GATT_STATE_SCAN_FAIL)
                    }
                }, SCAN_PERIOD)
                Log.i(TAG, "Starting scan for ${SCAN_PERIOD} ms");
                scanning = true
                sendUpdateGattStateBroadcast(GATT_STATE_SCANNING)
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
        return displayCharacteristic != null
    }

    fun update() {
        if (!isTargetWritten) {
            writeTargetDir()
        }
    }

    private fun writeTargetDir() {
        if (!isBtDeviceReadyForAccess()) {
            Log.w(TAG, "Attempting to access device which is not ready")
            isTargetWritten = false
        }
        bluetoothGatt?.let { gatt ->
            displayCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            displayCharacteristic?.value = ByteArray(5).apply {
                targetDirData.meters.let {
                    this[0] = targetDirData.dir.ordinal.toByte()
                    this[1] = (it shr 24).toByte()
                    this[2] = (it shr 16).toByte()
                    this[3] = (it shr 8).toByte()
                    this[4] = (it shr 0).toByte()
                }
            }
            isTargetWritten = gatt.writeCharacteristic(displayCharacteristic ?: run {
                isTargetWritten = false
                return
            })
        } ?: Log.e(TAG, "Unable to write $targetDirData")
        isTargetWritten = false
    }

    private fun sendUpdateGattStateBroadcast(state: Int) {
        Intent().also {
            it.action = MainActivity.GATT_CONN_STATE_CHANGE_ACTION
            it.putExtra(MainActivity.GATT_CONN_STATE_CHANGE_EXTRA, state)
            context.sendBroadcast(it)
        }
    }

    fun straight() {
        targetDirData = DirData(Dir.STRAIGHT, 100)
        writeTargetDir()
    }

    fun left() {
        targetDirData = DirData(Dir.TURN_LEFT, 65)
        writeTargetDir()

    }

    fun right() {
        targetDirData = DirData(Dir.TURN_RIGHT, 32)
        writeTargetDir()
    }

    fun noDir() {
        targetDirData = DirData(Dir.NO_DIR, 40)
        writeTargetDir()
    }

    fun isBtEnabled(): Boolean = bluetoothAdapter.isEnabled
}