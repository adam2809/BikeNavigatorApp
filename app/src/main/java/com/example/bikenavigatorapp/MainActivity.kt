package com.example.bikenavigatorapp

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothManager:BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private val handler = Handler()

    var bluetoothGatt: BluetoothGatt? = null
    var displayCharacteristic:BluetoothGattCharacteristic? = null


    var bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
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
                if (this!=null){
                    Log.i(TAG,"Found characteristic $this")
                }else{
                    Log.e(TAG,"Characteristic was not found")
                }
            };

        }
    }

    private companion object {
        const val REQUEST_ENABLE_BT = 0;
        const val SCAN_PERIOD: Long = 2000;
        const val TAG = "MainActivity";
        const val DEVICE_ADDRESS = "7C:9E:BD:06:E4:AA";
        const val SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
        const val CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
        const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        const val LOCATION_PERMISSION_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner= bluetoothAdapter.bluetoothLeScanner
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    private fun scanLeDevice(ifFoundCb:(ScanResult) -> Unit) {
        var found:ScanResult? = null

        val leScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                Log.d(TAG, "New scan result device: $result");
                if(result.device.address == DEVICE_ADDRESS){
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
                Log.i(TAG, "Starting scan for $SCAN_PERIOD ms");
                scanning = true
                scanner.startScan(leScanCallback)
            }
        }
    }

    fun blutacz(v: View){
        scanLeDevice { res ->
            bluetoothGatt = res.device.connectGatt(this, false, bluetoothGattCallback)
        }
    }

    private fun isBtDeviceReadyForAccess():Boolean{
        return displayCharacteristic != null // && bluetoothManager?.getConnectionState(bluetoothGatt?.device) == BluetoothProfile.STATE_CONNECTED
    }

    fun straight(v: View){
        if(!isBtDeviceReadyForAccess()){
            Log.w(TAG,"Attempting to access device which is not ready")
            return
        }
        bluetoothGatt?.let { gatt ->
            displayCharacteristic?.writeType = WRITE_TYPE_DEFAULT
            displayCharacteristic?.value = ByteArray(1){ 2 }
            gatt.writeCharacteristic(displayCharacteristic)
        } ?: Log.e(TAG,"Unable to write straight")
    }
    fun left(v: View){
        if(!isBtDeviceReadyForAccess()){
            Log.w(TAG,"Attempting to access device which is not ready")
            return
        }

        bluetoothGatt?.let { gatt ->
            displayCharacteristic?.writeType = WRITE_TYPE_DEFAULT
            displayCharacteristic?.value = ByteArray(1){ 1 }
            gatt.writeCharacteristic(displayCharacteristic)
        } ?: Log.e(TAG,"Unable to write straight")
    }
    fun right(v: View){
        if(!isBtDeviceReadyForAccess()){
            Log.w(TAG,"Attempting to access device which is not ready")
            return
        }

        bluetoothGatt?.let { gatt ->
            displayCharacteristic?.writeType = WRITE_TYPE_DEFAULT
            displayCharacteristic?.value = ByteArray(1){ 3 }
            gatt.writeCharacteristic(displayCharacteristic)
        } ?: Log.e(TAG,"Unable to write straight")
    }

    private fun findCharacteristic(gatt:BluetoothGatt?):BluetoothGattCharacteristic?{
        val found = gatt!!.services.let {
            it.filter { service -> service.also { Log.d(TAG,"Service in gatt discovered: ${service.uuid}") }.uuid == UUID.fromString(SERVICE_UUID); }
        }.flatMap {
            it.characteristics
        }.let {
            it.filter { characteristic -> characteristic.also { Log.d(TAG,"Characteristic in gatt discovered: ${characteristic.uuid}") }.uuid == UUID.fromString(CHARACTERISTIC_UUID) }
        }
        return found.elementAtOrNull(0);
    }
}