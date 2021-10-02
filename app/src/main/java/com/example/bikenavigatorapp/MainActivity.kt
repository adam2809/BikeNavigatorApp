package com.example.bikenavigatorapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bikenavigatorapp.NavigationService.LocalBinder

class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = "${MainActivity::class.java.simpleName}(bnalt)"
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        const val LOCATION_PERMISSION_REQUEST_CODE = 2
    }

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mBound = true
            mService = binder.service.also { boundService ->
                sharePlaceUrl?.let {
                    boundService.setNewDestination(it)
                    sharePlaceUrl = null
                } ?: run {
                    Log.w(TAG, "Trying to update navigation in service without dest url provided")
                }
                updateConnectionStatusTextView(boundService.getBleStatus())
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    var sharePlaceUrl: String? = null

    // A reference to the service used to get location updates.
    private var mService: NavigationService? = null

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

        if (intent?.action == Intent.ACTION_SEND) {
            sharePlaceUrl = getSharePlaceUrlFromIntent()
        }

        registerGattConnStateChangeReceiver()
        updateConnectionStatusTextView(BluetoothProfile.STATE_DISCONNECTED)
    }

    override fun onResume() {
        super.onResume()
        promptEnableBluetooth()
    }

    override fun onStart() {
        val res = bindService(
            Intent(this, NavigationService::class.java), mServiceConnection,
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
                    Log.w(TAG, "Location permissions request failed requesting again")
                    requestLocationPermissions()
                } else {
                    Log.i(TAG, "Successfuly granted location permissions")
                }
            }
        }
    }

    private fun getSharePlaceUrlFromIntent(): String? {
        val text = intent.extras?.get("android.intent.extra.TEXT")?.toString() ?: run {
            Log.w(TAG, "Intent is missing TEXT extra")
            return null
        }
        Log.d(TAG, "Text extra of intent: $text")
        val urlResult = SHARE_PLACE_URL_REGEX.find(text)
        val url = urlResult?.groupValues?.firstOrNull() ?: run {
            Log.w(TAG, "Could not find url in TEXT extra")
            return null
        }

        Log.d(TAG, "URL is: $url")
        return url
    }

    private fun promptEnableBluetooth() {
        val adapter = ContextCompat.getSystemService(
            this,
            BluetoothManager::class.java
        )?.adapter ?: run {
            Log.e(TAG, "Bt manager or adapter are anavailable")
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    private fun registerGattConnStateChangeReceiver() {
        val gattConnStateChangeBr = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.extras?.getInt(BleDirDisplay.GATT_CONN_STATE_CHANGE_EXTRA)?.let {
                    Log.d(TAG, "Receiving gatt conn state change broadcast")
                    updateConnectionStatusTextView(it)
                }
            }
        }

        val filter = IntentFilter(BleDirDisplay.GATT_CONN_STATE_CHANGE_ACTION)
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

    fun initiateBleScan(v: View) {
        mService?.bleScan() ?: run {
            Log.w(TAG, "Trying to initiate scan in unbound service")
        }
    }

    fun switchSpeedometer(view: View) {
        val isOn = mService?.switchSpeedometer() ?: run {
            Log.w(TAG, "Trying to switch speedometer in unbound service")
            return
        }

        val button = view as Button
        button.text = if (isOn) {
            resources.getString(R.string.stop_speedometer)
        } else {
            resources.getString(R.string.start_speedometer)
        }
    }

    fun quit(view: View) {
        stopService(Intent(applicationContext, NavigationService::class.java))
        finish()
    }
}