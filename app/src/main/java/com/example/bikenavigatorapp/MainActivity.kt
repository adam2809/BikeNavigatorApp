package com.example.bikenavigatorapp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bikenavigatorapp.geofencing.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.*


fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}

class MainActivity : AppCompatActivity() {
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val geofencingClient by lazy { LocationServices.getGeofencingClient(this) }
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private var scanning = false
    private val handler = Handler()

    var bluetoothGatt: BluetoothGatt? = null
    var displayCharacteristic:BluetoothGattCharacteristic? = null

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
                if (this!=null){
                    Log.i(TAG,"Found characteristic $this")
                }else{
                    Log.e(TAG,"Characteristic was not found")
                }
            };

        }
    }

    private companion object {
        const val SCAN_PERIOD: Long = 2000;
        const val TAG = "MainActivity";
        const val DEVICE_ADDRESS = "7C:9E:BD:06:E4:AA";
        const val DISPLAY_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
        const val DISPLAY_CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
        const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        const val LOCATION_PERMISSION_REQUEST_CODE = 2
        const val GEOFENCE_REQ_IDS_START = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                } else {
                    initiateScan()
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
        initiateScan()
    }

    private fun initiateScan(){
        if(!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
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


    fun buildGeofences(steps:List<DirApi.Step>):List<Geofence>{
        val res = mutableListOf<Geofence>()
        Geofence.Builder().apply {
            setRequestId(GEOFENCE_REQ_IDS_START.toString())
            setCircularRegion(
                steps.first().startLocation.lat,
                steps.first().startLocation.lng,
                50F
            )
            setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            setExpirationDuration(Geofence.NEVER_EXPIRE)

            res += build().also { Log.i(TAG,"Adding geofence: $it") }
        }

        Geofence.Builder().apply {
            setRequestId(GEOFENCE_REQ_IDS_START.toString())
            setCircularRegion(
                steps.last().endLocation.lat,
                steps.last().endLocation.lng,
                50F
            )
            setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            setExpirationDuration(Geofence.NEVER_EXPIRE)

            res += build().also { Log.i(TAG,"Adding geofence: $it") }
        }

        return res
//        steps.map { step ->
//        }
    }


    fun updateSteps(v: View){
        val nav = DirApi(this)
        nav.updateSteps()
    }

    fun setupGeofences(steps: List<DirApi.Step>){
        val geofences = buildGeofences(steps)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG,"No permission granted")
        }
        geofencingClient
            .addGeofences(buildGeofencingRequest(geofences), geofencePendingIntent)
            .addOnSuccessListener {
                Log.i(TAG,"Successfuly submitted geofences")
            }
            .addOnFailureListener {
                Log.e(TAG,"Error while submitting geofences: ${it.message}")
            }
    }

    private fun buildGeofencingRequest(geofences: List<Geofence>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
    }


    private fun findCharacteristic(gatt:BluetoothGatt?):BluetoothGattCharacteristic?{
        val found = gatt!!.services.let {
            it.filter { service -> service.also { Log.d(TAG,"Service in gatt discovered: ${service.uuid}") }.uuid == UUID.fromString(DISPLAY_SERVICE_UUID); }
        }.flatMap {
            it.characteristics
        }.let {
            it.filter { characteristic -> characteristic.also { Log.d(TAG,"Characteristic in gatt discovered: ${characteristic.uuid}") }.uuid == UUID.fromString(DISPLAY_CHARACTERISTIC_UUID) }
        }
        return found.elementAtOrNull(0);
    }
}