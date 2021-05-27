package com.example.bikenavigatorapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity";
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        const val LOCATION_PERMISSION_REQUEST_CODE = 2
        const val GATT_CONN_STATE_CHANGE_ACTION =
            "com.example.bikenavigatorapp.GATT_CONN_STATE_CHANGE_ACTION"
        const val GATT_CONN_STATE_CHANGE_EXTRA =
            "com.example.bikenavigatorapp.GATT_CONN_STATE_CHANGE_EXTRA"
    }

    private val locationCb by lazy {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.let { res ->
                    nav?.location = res.locations.firstOrNull()?.also {
                        Log.d(TAG, "Got location: ${it.latitude}, ${it.longitude}")
                    } ?: run {
                        Log.w(TAG, "Location update useless since Navigator is null")
                        return@let
                    }
                } ?: Log.w(TAG, "Location result is null")
            }
        }
    }

    val dirDisplay = BleDirDisplay(this)
    lateinit var dirs: DirApi
    var nav: Navigator? = null
    private val locClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(
            this
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (intent == null) {
            Log.i(TAG, "Intent is null")
            return
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> startNavFromGMapsShare()
        }

        registerGattConnStateChangeReceiver()
        updateConnectionStatusTextView(BluetoothProfile.STATE_DISCONNECTED)
    }

    override fun onResume() {
        super.onResume()
        promptEnableBluetooth()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        dirDisplay.bluetoothGatt?.disconnect()
    }

    private fun startNavFromGMapsShare() {
        val text = intent.extras?.get("android.intent.extra.TEXT")?.toString() ?: run {
            Log.w(TAG, "Intent is missing TEXT extra")
            return
        }
        val urlResult = HTTPS_REGEX.find(text)
        val url = urlResult?.groupValues?.firstOrNull() ?: run {
            Log.w(TAG, "Could not find url in TEXT extra")
            return
        }
        Log.i(TAG, "URL is: $url")
        startNewNav(url)
    }

    @SuppressLint("MissingPermission")
    private fun startNewNav(sharePlaceUrl: String) {
        locClient.lastLocation.addOnSuccessListener listener@{ currLoc ->
            dirs = DirApi(this) {
                nav = Navigator(dirs.steps, currLoc, dirDisplay)
                startLocationUpdates()
            }
            dirs.updateSteps(DirApi.Location(currLoc.latitude, currLoc.longitude), sharePlaceUrl)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions()
            return
        }

        locClient.requestLocationUpdates(
            LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 2000
            },
            locationCb,
            Looper.getMainLooper()
        ).addOnSuccessListener {
            Log.i(TAG, "Location updates request successful")
        }.addOnFailureListener {
            Log.e(TAG, "Location updates request failed")
        }
    }

    private fun stopLocationUpdates() {
        locClient.removeLocationUpdates(locationCb)
    }

    private fun promptEnableBluetooth() {
        if (!dirDisplay.isBtEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    private fun registerGattConnStateChangeReceiver() {
        val gattConnStateChangeBr = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateConnectionStatusTextView(
                    intent?.extras?.getInt(GATT_CONN_STATE_CHANGE_EXTRA) ?: run {
                        Log.w(TAG, "No GATT_CONN_STATE_CHANGE_EXTRA provided")
                        return
                    })
            }
        }

        val filter = IntentFilter(GATT_CONN_STATE_CHANGE_ACTION)
        registerReceiver(gattConnStateChangeBr, filter)
    }

    fun updateConnectionStatusTextView(state: Int) {
        findViewById<TextView>(R.id.connStatusTextView).apply {
            text = String.format(
                resources.getString(R.string.connection_status),
                state.connStatusToString()
            )
        }
    }

    private fun Int.connStatusToString(): String {
        return when (this) {
            BluetoothProfile.STATE_CONNECTED -> "Connected"
            BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
            BluetoothProfile.STATE_CONNECTING -> "Connecting..."
            BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
            BleDirDisplay.GATT_STATE_SCANNING -> "Scannning..."
            BleDirDisplay.GATT_STATE_SCAN_SUCCESS -> "Scan successful"
            BleDirDisplay.GATT_STATE_SCAN_FAIL -> "Scan failed"
            else -> ""
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

    fun hasLocationPermissions(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
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
                    Log.w(TAG, "Location permissions request failed requesting again")
                    requestLocationPermissions()
                } else {
                    Log.i(TAG, "Successfuly granted location permissions")
                }
            }
        }
    }


    fun blutacz(v: View) {
        dirDisplay.initiateScan()
    }

    fun straight(v: View) {
        dirDisplay.straight()
    }

    fun left(v: View) {
        dirDisplay.left()

    }

    fun right(v: View) {
        dirDisplay.right()
    }
}