package com.example.bikenavigatorapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.bikenavigatorapp.LocationUpdatesService.LocalBinder


//TODO like this private static final String TAG = LocationUpdatesService.class.getSimpleName(); everywhere
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity";
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        const val LOCATION_PERMISSION_REQUEST_CODE = 2
    }

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    val dirDisplay = BleDirDisplay(this)
    lateinit var dirs: DirApi
    var nav: Navigator? = null

    // A reference to the service used to get location updates.
    private var mService: LocationUpdatesService? = null

    // Tracks the bound state of the service.
    private var mBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasLocationPermissions() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestLocationPermissions()
        }

        if (intent == null) {
            Log.i(TAG, "Intent is null")
            return
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> startNavFromGMapsShare()
        }

        registerGattConnStateChangeReceiver()
        registerLocationUpdateReceiver()
        updateConnectionStatusTextView(BluetoothProfile.STATE_DISCONNECTED)
    }

    override fun onResume() {
        super.onResume()
        promptEnableBluetooth()
    }

    override fun onStart() {
        val res = bindService(
            Intent(this, LocationUpdatesService::class.java), mServiceConnection,
            BIND_AUTO_CREATE
        )
        if (res) {
            Log.i(TAG, "Successfully bound service")
        } else {
            Log.w(TAG, "Failed bound service")
        }
        super.onStart()
    }

    override fun onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            Log.i(TAG, "Unbinding service")
            unbindService(mServiceConnection);
            mBound = false;
        }

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        mService?.let { service ->
            service.updateLastLocation()
            service.mLocation?.let { loc ->
                dirs = DirApi(this) {
                    nav = Navigator(it, loc, dirDisplay)
                    service.requestLocationUpdates()
                }
                dirs.updateSteps(DirApi.Location(loc.latitude, loc.longitude), sharePlaceUrl)
            } ?: run {
                Log.e(TAG, "Could not get initial location")
            }
        } ?: run {
            Log.e(TAG, "mService is null")
        }
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
                    intent?.extras?.getInt(BleDirDisplay.GATT_CONN_STATE_CHANGE_EXTRA) ?: run {
                        Log.w(TAG, "No GATT_CONN_STATE_CHANGE_EXTRA provided")
                        return
                    })
            }
        }

        val filter = IntentFilter(BleDirDisplay.GATT_CONN_STATE_CHANGE_ACTION)
        registerReceiver(gattConnStateChangeBr, filter)
    }

    private fun registerLocationUpdateReceiver() {
        val locUpdateBr = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val loc: Location =
                    intent.getParcelableExtra(LocationUpdatesService.LOCATION_UPDATE_EXTRA) ?: run {
                        Log.w(TAG, "No LOCATION_UPDATE_EXTRA provided")
                        return
                    }

                nav?.let {
                    it.location = loc
                } ?: run {
                    Log.w(TAG, "Location update useless since Navigator is null")
                    return
                }
            }
        }

        val filter = IntentFilter(LocationUpdatesService.LOCATION_UPDATE_ACTION)
        registerReceiver(locUpdateBr, filter)

        LocalBroadcastManager.getInstance(this).registerReceiver(locUpdateBr, filter);
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

    private fun hasLocationPermissions(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestLocationPermissions() {
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