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
        private val TAG = "${BleDirDisplay::class.java.simpleName}(bnalt)"
        const val SCAN_PERIOD: Long = 500;
        const val DEVICE_ADDRESS = "24:A1:60:7F:1F:CE";
        const val DISPLAY_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
        const val DISPLAY_CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
        const val MODE_CHARACTERISTIC_UUID = "0000ff02-0000-1000-8000-00805f9b34fb";
        const val GATT_STATE_SCANNING = 4;
        const val GATT_STATE_SCAN_SUCCESS = 5;
        const val GATT_STATE_SCAN_FAIL = 6;
        private const val PACKAGE_NAME = "com.example.bikenavigatorapp"
        const val GATT_CONN_STATE_CHANGE_ACTION = "$PACKAGE_NAME.GATT_CONN_STATE_CHANGE_ACTION"
        const val GATT_CONN_STATE_CHANGE_EXTRA = "$PACKAGE_NAME.GATT_CONN_STATE_CHANGE_EXTRA"
        const val DIR_DATA_LENGTH = 6
    }

    data class DirData(var dir: Dir, var meters: Int, var speed: Int, var mode: Mode)

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

    enum class Mode {
        NOTHING,
        NAVIGATION,
        SPEEDOMETER
    }

    private val bluetoothManager by lazy { getSystemService(context, BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager!!.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var scanning = false

    private val handler = Handler()
    var bluetoothGatt: BluetoothGatt? = null

    var displayCharacteristic: BluetoothGattCharacteristic? = null
    var modeCharacteristic: BluetoothGattCharacteristic? = null
    var targetDirData: DirData = DirData(Dir.NO_DIR, 0, 0, Mode.NOTHING)
        set(value) {
            if (value != field) {
                isTargetWritten = false
            }
            field = value
        }
    private val displayCharacteristicValue: ByteArray
        get() = ByteArray(DIR_DATA_LENGTH).apply {
            this[0] = targetDirData.dir.ordinal.toByte()
            targetDirData.meters.let {
                this[1] = (it shr 24).toByte()
                this[2] = (it shr 16).toByte()
                this[3] = (it shr 8).toByte()
                this[4] = (it shr 0).toByte()
            }
            this[5] = targetDirData.speed.toByte()
        }
    private val modeCharacteristicValue: ByteArray
        get() = ByteArray(1).apply {
            this[0] = targetDirData.mode.ordinal.toByte()
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
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")

                    bluetoothGatt = null
                    displayCharacteristic = null
                    modeCharacteristic = null
                }
                BluetoothProfile.STATE_CONNECTING -> Log.i(TAG, "Connecting to GATT server.")
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.i(TAG, "Disconnecting from GATT server.")

                    bluetoothGatt = null
                    displayCharacteristic = null
                    modeCharacteristic = null
                }
            }
            sendUpdateGattStateBroadcast(newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            displayCharacteristic = findCharacteristic(
                DISPLAY_SERVICE_UUID,
                DISPLAY_CHARACTERISTIC_UUID
            ).apply {
                if (this != null) {
                    Log.i(TAG, "Found display characteristic $this")
                } else {
                    Log.e(TAG, "Characteristic display was not found")
                }
            }
            modeCharacteristic = findCharacteristic(
                DISPLAY_SERVICE_UUID,
                MODE_CHARACTERISTIC_UUID
            ).apply {
                if (this != null) {
                    Log.i(TAG, "Found mode characteristic $this")
                } else {
                    Log.e(TAG, "Characteristic mode was not found")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            Log.d(
                TAG,
                "Write result of characteristic with uuid=${characteristic?.uuid} was $status"
            )
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


    private fun findCharacteristic(
        serviceUuid: String,
        charUuid: String
    ): BluetoothGattCharacteristic? {
        val found = bluetoothGatt!!.services.let {
            it.filter { service ->
                service.also {
                    Log.d(
                        TAG,
                        "Service in gatt found: ${service.uuid}"
                    )
                }.uuid == UUID.fromString(serviceUuid);
            }
        }.flatMap {
            it.characteristics
        }.let {
            it.filter { characteristic ->
                characteristic.also {
                    Log.d(
                        TAG,
                        "Characteristic in gatt found: ${characteristic.uuid}"
                    )
                }.uuid == UUID.fromString(charUuid)
            }
        }
        return found.elementAtOrNull(0);
    }

    fun isBtDeviceReadyForAccess(): Boolean {
        return displayCharacteristic != null && modeCharacteristic != null
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

        isTargetWritten =
            writeCharacteristic(DISPLAY_CHARACTERISTIC_UUID, displayCharacteristicValue)
        val handler = Handler()
        handler.postDelayed({
            isTargetWritten = writeCharacteristic(MODE_CHARACTERISTIC_UUID, modeCharacteristicValue)

            if (!isTargetWritten) {
                Log.d(TAG, "Could not init write of characteristic")
            }
        }, 5000)
    }

    private fun writeCharacteristic(uuid: String, data: ByteArray): Boolean {
        Log.d(
            TAG,
            "Trying to write characteristic with uuid=$uuid and data=${data.map { "$it, " }}"
        )
        val service = bluetoothGatt?.getService(UUID.fromString(DISPLAY_SERVICE_UUID))
        val char = service?.getCharacteristic(UUID.fromString(uuid))
        char?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        char?.value = data
        return bluetoothGatt?.writeCharacteristic(char ?: run {
            return false
        }) ?: run {
            return false
        }
    }

    private fun sendUpdateGattStateBroadcast(state: Int) {
        Intent().also {
            it.action = GATT_CONN_STATE_CHANGE_ACTION
            it.putExtra(GATT_CONN_STATE_CHANGE_EXTRA, state)
            context.sendBroadcast(it)
        }
    }
}