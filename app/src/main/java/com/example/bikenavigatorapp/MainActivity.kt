package com.example.bikenavigatorapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class MainActivity : AppCompatActivity() {
    private val locationCb by lazy {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.let { res ->
                    nav.location = res.locations.firstOrNull()?.also {
                        Log.d(TAG, "Got location: ${it.latitude}, ${it.longitude}")
                    }
                } ?: Log.w(TAG, "Location result is null")
            }
        }
    }

    val dirDisplay =  BleDirDisplay(this)
    val dirs by lazy { DirApi(this) }
    private val nav by lazy { Navigator(this) }
    val locClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val TAG = "MainActivity";
    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    val LOCATION_PERMISSION_REQUEST_CODE = 2


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (intent == null) {
            Log.i(TAG, "Intent is null")
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> startNavFromGMapsShare()
        }
    }

    fun startNavFromGMapsShare() {
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

        startLocationUpdates()
        dirs.updateStepsFromSharePlaceUrl(url)
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

    override fun onResume() {
        super.onResume()
        promptEnableBluetooth()
    }

    private fun promptEnableBluetooth() {
        if (!dirDisplay.isBtEnabled()) {
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

    fun updateSteps(v: View) {
    }
}