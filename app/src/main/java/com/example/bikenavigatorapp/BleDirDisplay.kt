package com.example.bikenavigatorapp

import android.app.PendingIntent
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import java.util.*


class BleDirDisplay(private val context: Context) {
    companion object {
        private val TAG = "${BleDirDisplay::class.java.simpleName}(bnalt)"
        const val SCAN_PERIOD: Long = 500;
        const val DEVICE_ADDRESS = "24:A1:60:7F:1F:CE"
        val DISPLAY_SERVICE_UUID: UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")!!
        val DIR_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")!!
        val METERS_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")!!
        val SPEED_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")!!
        val MODE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ff04-0000-1000-8000-00805f9b34fb")!!
        const val WAIT_FOR_DEVICE_TIMEOUT_MS = 300;
        const val GATT_STATE_SCANNING = 4;
        const val GATT_STATE_SCAN_SUCCESS = 5;
        const val GATT_STATE_SCAN_FAIL = 6;
        private const val PACKAGE_NAME = "com.example.bikenavigatorapp"
        const val GATT_SCAN_RES_ACTION = "$PACKAGE_NAME.GATT_SCAN_RES_ACTION"

        const val GATT_CONN_STATE_CHANGE_ACTION = "$PACKAGE_NAME.GATT_CONN_STATE_CHANGE_ACTION"
        const val GATT_CONN_STATE_CHANGE_EXTRA = "$PACKAGE_NAME.GATT_CONN_STATE_CHANGE_EXTRA"
    }

    var currMode: Mode = Mode.NOTHING
    var currMeters = 0

    var gattConnStatus = 0
    private val bluetoothManager by lazy { getSystemService(context, BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager!!.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var scanning = false

    var bluetoothGatt: BluetoothGatt? = null

    private val uuidToDataMappingCharsToWrite = mutableMapOf<UUID, ByteArray>()
    private val lastScanIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(GATT_SCAN_RES_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT
    )

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
                }
                BluetoothProfile.STATE_CONNECTING -> Log.i(TAG, "Connecting to GATT server.")
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.i(TAG, "Disconnecting from GATT server.")

                    bluetoothGatt = null
                }
            }
            sendUpdateGattStateBroadcast(newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (characteristicsSanityCheck()) {
                Log.i(TAG, "All necessary uuids present on discovery")
            } else {
                Log.e(TAG, "Missing characteristics or service on discovery")
            }
        }
    }

    fun connectBleScanResult(res: ScanResult) {
        bluetoothGatt = res.device.connectGatt(context, false, bluetoothGattCallback)
    }

    fun startScan() {
        if (scanning) {
            return
        }
        scanning = true

        sendUpdateGattStateBroadcast(GATT_STATE_SCANNING)

        val settings = ScanSettings.Builder().build()
        val filter = ScanFilter.Builder().setDeviceAddress(DEVICE_ADDRESS).build()
        bluetoothLeScanner.startScan(listOf(filter), settings, lastScanIntent)
    }

    //    TODO should set the state to disconnected in case nothing got connected during the scan
    fun stopScan() {
        if (!scanning) {
            return
        }
        scanning = false

        bluetoothLeScanner.stopScan(lastScanIntent)
    }

    fun requestCharacteristicUpdate(uuid: UUID, data: Byte) {
        if (uuid == MODE_CHARACTERISTIC_UUID) {
            currMode = Mode.values()[data.toInt()]
        }

        uuidToDataMappingCharsToWrite[uuid] = byteArrayOf(data)
    }

    fun requestCharacteristicUpdate(uuid: UUID, data: Int) {
        if (uuid == METERS_CHARACTERISTIC_UUID) {
            currMeters = data
        }
        uuidToDataMappingCharsToWrite[uuid] = data.toByteArray()
    }

    fun <E : Enum<E>> requestCharacteristicUpdate(uuid: UUID, data: E) {
        requestCharacteristicUpdate(uuid, data.ordinal.toByte())
    }

    fun update() {
        writeRequestedCharacteristics()
    }

    private fun writeRequestedCharacteristics() {
        if (!characteristicsSanityCheck()) {
            Log.w(TAG, "Attempting to access device which is not ready")
        }

        val (succeeded, failed) = uuidToDataMappingCharsToWrite.entries.partition { (uuid, data) ->
            writeCharacteristic(uuid, data)
        }

        if (failed.isNotEmpty()) {
            Log.d(TAG, "Could not init writes of characteristics with uuids = ${
                failed.map { (uuid, _) -> "${uuid}, " }
            }")
        }

        succeeded.forEach {
            uuidToDataMappingCharsToWrite.remove(it.key)
        }
        Log.d(TAG, "Successfull init writes of characteristics with uuids = ${
            succeeded.map { (uuid, _) -> "${uuid}, " }
        }")
    }

    private fun writeCharacteristic(
        characterUUID: UUID,
        value: ByteArray,
        serviceUUID: UUID = DISPLAY_SERVICE_UUID
    ): Boolean {
        val gatt = bluetoothGatt ?: run {
            return false
        }

        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < WAIT_FOR_DEVICE_TIMEOUT_MS) {
            if (gatt.isDeviceBusy()) {
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            } else {
                break
            }
        }

        return try {
            val service = gatt.getService(serviceUUID)
            val characteristic = service.getCharacteristic(characterUUID)
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sendUpdateGattStateBroadcast(state: Int) {
        Intent().also {
            it.action = GATT_CONN_STATE_CHANGE_ACTION
            it.putExtra(GATT_CONN_STATE_CHANGE_EXTRA, state)
            context.sendBroadcast(it)
        }
    }

    private fun BluetoothGatt.isDeviceBusy(): Boolean {
        var state = false
        try {
            val busyField = BluetoothGatt::class.java.getDeclaredField("mDeviceBusy")
            busyField.isAccessible = true
            state = busyField.get(this) as Boolean
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        }
        return state
    }


    fun characteristicsSanityCheck(): Boolean {
        val service = bluetoothGatt?.getService(DISPLAY_SERVICE_UUID) ?: run {
            Log.d(TAG, "Could not find display service")
            return false
        }

        service.getCharacteristic(DIR_CHARACTERISTIC_UUID) ?: run {
            Log.d(TAG, "Could not find dir char")
            return false
        }
        service.getCharacteristic(METERS_CHARACTERISTIC_UUID) ?: run {
            Log.d(TAG, "Could not find meters char")
            return false
        }
        service.getCharacteristic(SPEED_CHARACTERISTIC_UUID) ?: run {
            Log.d(TAG, "Could not find speed char")
            return false
        }
        service.getCharacteristic(MODE_CHARACTERISTIC_UUID) ?: run {
            Log.d(TAG, "Could not find mode char")
            return false
        }
        return true
    }

    private fun getCurrCharValue(uuid: UUID, dataLen: Int): ByteArray {
        val zerosRet = ByteArray(dataLen)
        zerosRet.fill(0)
        bluetoothGatt?.getService(DISPLAY_SERVICE_UUID)
            ?.getCharacteristic(uuid)?.let {
                if (it.value != null) {
                    return it.value
                } else {
                    zerosRet.fill(0)
                    return zerosRet
                }
            } ?: run {
            Log.w(TAG, "Trying to access char($uuid) value when it is unavailable")
            return zerosRet
        }
    }
}