package com.example.bikenavigatorapp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import com.example.bikenavigatorapp.geofencing.GeofenceBroadcastReceiver
import com.example.bikenavigatorapp.geofencing.GeofenceErrorMessages
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
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
    val dirs by lazy { DirApi(this) }

    private val dirDisplay = BleDirDisplay(this)

    private val TAG = "MainActivity";
    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    val LOCATION_PERMISSION_REQUEST_CODE = 2
    private val GEOFENCE_REQ_IDS_START = 100
    private val GEOFENCE_RADIUS = 20F


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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


    private fun generateGeofences(steps: List<DirApi.Step>): List<Geofence> {
        var i = 0
        return steps.flatMap { step ->
            listOf(
                buildGeofence(
                    step.startLocation,
                    GEOFENCE_REQ_IDS_START + i,
                    Geofence.GEOFENCE_TRANSITION_ENTER
                ).also { Log.i(TAG, "Adding geofence: $it") },
                buildGeofence(
                    step.endLocation,
                    GEOFENCE_REQ_IDS_START + i + 1,
                    Geofence.GEOFENCE_TRANSITION_EXIT
                ).also { Log.i(TAG, "Adding geofence: $it") }
            ).also { i += 2 }
        }
    }

    private fun buildGeofence(loc: DirApi.Location, reqId: Int, transType: Int): Geofence {
        return Geofence.Builder().apply {
            setRequestId(reqId.toString())
            setCircularRegion(
                loc.lat,
                loc.lng,
                GEOFENCE_RADIUS
            )
            setTransitionTypes(transType)
            setExpirationDuration(Geofence.NEVER_EXPIRE)
        }.build()
    }


    fun updateSteps(v: View){
        dirs.updateSteps()
    }

    fun setupGeofences(steps: List<DirApi.Step>){
        val geofences = generateGeofences(steps)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG,"No permission granted")
        }
        geofencingClient
            .addGeofences(buildGeofencingRequest(geofences), geofencePendingIntent)
            .addOnSuccessListener {
                Log.i(TAG, "Successfully submitted geofences")
            }
            .addOnFailureListener {
                Log.e(TAG, "Error while submitting geofences: ${it.message}")
            }
    }

    private fun buildGeofencingRequest(geofences: List<Geofence>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
    }

    class GeofenceTransitionsJobIntentService : JobIntentService() {
        companion object {
            const val TAG = "JobIntentService";
            private const val JOB_ID = 573

            fun enqueueWork(context: Context, intent: Intent) {
                enqueueWork(
                    context,
                    GeofenceTransitionsJobIntentService::class.java, JOB_ID, intent
                )
            }
        }

        override fun onHandleWork(intent: Intent) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceErrorMessages.getErrorString(
                    this,
                    geofencingEvent.errorCode
                )
                Log.e(TAG, errorMessage)
                return
            }

            Log.i(
                TAG,
                "Handling ${geofencingEvent.triggeringLocation.latitude}, ${geofencingEvent.triggeringLocation.longitude} (${geofencingEvent.geofenceTransition})"
            )
            Log.d(
                TAG,
                "Geofences that triggered  size: ${geofencingEvent.triggeringGeofences.size}"
            )

            val geofence = geofencingEvent.triggeringGeofences.first()
            val reqId = geofence.requestId.toInt()
//            val step =
            if (reqId % 2 == 0) {

            }
        }
    }
}