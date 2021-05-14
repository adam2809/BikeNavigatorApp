package com.example.bikenavigatorapp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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


class MainActivity : AppCompatActivity() {
    private val geofencingClient by lazy { LocationServices.getGeofencingClient(this) }
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    val displayChangeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dir = when (intent.extras?.get("dir") as String) {
                BleDirDisplay.Dir.NO_DIR.toString() -> BleDirDisplay.Dir.NO_DIR
                BleDirDisplay.Dir.LEFT.toString() -> BleDirDisplay.Dir.LEFT
                BleDirDisplay.Dir.RIGHT.toString() -> BleDirDisplay.Dir.RIGHT
                BleDirDisplay.Dir.STRAIGHT.toString() -> BleDirDisplay.Dir.STRAIGHT
                else -> {
                    Log.w(TAG, "Received intent with invalid direction")
                    return
                }
            }
            Log.i(TAG,"Trying to write $dir to display")
            dirDisplay.writeDir(dir)
        }
    }

    private val dirDisplay = BleDirDisplay(this)

    private val TAG = "MainActivity";
    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    val LOCATION_PERMISSION_REQUEST_CODE = 2
    private val GEOFENCE_REQ_IDS_START = 100
    private val GEOFENCE_RADIUS = 30F


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(displayChangeBroadcastReceiver, IntentFilter("DISPLAY_DIR_CHANGE"));
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
                    dirDisplay.initiateScan()
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