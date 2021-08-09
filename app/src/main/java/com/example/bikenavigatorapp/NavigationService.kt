package com.example.bikenavigatorapp

import android.app.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt

/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification associated with that service is removed.
 */
class NavigationService : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false
    private lateinit var mNotificationManager: NotificationManager

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private lateinit var mLocationRequest: LocationRequest

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mServiceHandler: Handler

    /**
     * The current location.
     */
    var mLocation: Location? = null

    private lateinit var dirDisplay: BleDirDisplay
    private var nav: Navigator? = null
    private lateinit var dirs: DirApi

//    var gattConnStateChangeCb: ((Int) -> Unit)? = null

    override fun onCreate() {
        dirDisplay = BleDirDisplay(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }
        createLocationRequest()
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        dirs = DirApi(this) { steps ->
            updateLastLocation onSuccess@{
                nav = Navigator(
                    steps,
                    mLocation ?: run {
                        Log.e(TAG, "Location is null")
                        return@onSuccess
                    },
                    dirDisplay
                )

                requestLocationUpdates()
            }
        }

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel =
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel)
        }

        registerGattConnStateChangeReceiver()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(
            EXTRA_STARTED_LOC_SERVICE_FROM_NOTIFICATION,
            false
        )

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && isRequestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")
            startForeground(NOTIFICATION_ID, notification)
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        Log.i(TAG,"Destroying service")
        mServiceHandler.removeCallbacksAndMessages(null)
        dirDisplay.bluetoothGatt?.disconnect()
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    private fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")
        setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, NavigationService::class.java))
        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.myLooper() ?: run {
                    Log.w(TAG,"Looper is null")
                    return
                }
            )
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, false)
            Log.e(
                TAG,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    private fun removeLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, true)
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }// Channel ID// Extra to help us figure out if we arrived in onStartCommand via the notification or not.

    // The PendingIntent that leads to a call to onStartCommand() in this service.

    // The PendingIntent to launch activity.

    // Set the Channel ID for Android O.
    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private val notification: Notification
        get() {
            val intent = Intent(this, NavigationService::class.java)
            val text: CharSequence = getLocationText(mLocation)

            // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
            intent.putExtra(EXTRA_STARTED_LOC_SERVICE_FROM_NOTIFICATION, true)

            // The PendingIntent that leads to a call to onStartCommand() in this service.
            val servicePendingIntent = PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            // The PendingIntent to launch activity.
            val activityPendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java), 0
            )
            val builder = NotificationCompat.Builder(this)
                .addAction(
                    R.drawable.ic_launch, getString(R.string.launch_activity),
                    activityPendingIntent
                )
                .addAction(
                    R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                    servicePendingIntent
                )
                .setContentText(text)
                .setContentTitle(getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID) // Channel ID
            }
            return builder.build()
        }

    private fun updateLastLocation(onSuccess: (Location) -> Unit) {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        mLocation = task.result
                        onSuccess(task.result)
                    } else {
                        Log.w(TAG, "Failed to get location.")
                    }
                }
        } catch (unlikely: SecurityException) {
            Log.e(
                TAG,
                "Lost location permission.$unlikely"
            )
        }
    }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")
        mLocation = location

        nav?.apply {
            this.location = location
        } ?: run {
            Log.w(TAG, "Location updating with null navigation")
        }

        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.SPEED_CHARACTERISTIC_UUID,
            (location.speed * MPS_TO_KPH_COEFFICIENT).roundToInt().toByte()
        )

        dirDisplay.update()
    }

    fun setNewDestination(destUrl: String) {
        updateLastLocation onSuccess@{
            mLocation?.let {
                dirs.updateSteps(it.toDirApiLocation(), destUrl)
            } ?: run {
                Log.w(TAG, "Could not get current location")
                return@onSuccess
            }
        }
    }


    private fun registerGattConnStateChangeReceiver() {
        val gattConnStateChangeBr = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.extras?.getInt(BluetoothLeScanner.EXTRA_CALLBACK_TYPE)?.let { extra ->
                    Log.d(TAG, "Receiving ble scan results with callback type $extra")
                    val scanResults = intent.getParcelableArrayListExtra<ScanResult>(
                        BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
                    )
                    scanResults?.firstOrNull()?.let {
                        dirDisplay.connectBleScanResult(it)
                        dirDisplay.stopScan()
                    }
                }
            }
        }

        val filter = IntentFilter(BleDirDisplay.GATT_CONN_STATE_CHANGE_ACTION)
        registerReceiver(gattConnStateChangeBr, filter)
    }

    //TODO delete
    fun bleScan() {
        dirDisplay.startScan()
    }

    fun getBleStatus(): Boolean {
        return dirDisplay.characteristicsSanityCheck()
    }

    /**
     * Switches to and from speedometer mode
     * @return true if was switched on false if off
     */
    fun switchSpeedometer(): Boolean {
        if (dirDisplay.currMode == Mode.SPEEDOMETER) {
            if (nav == null && isRequestingLocationUpdates(this)) {
                dirDisplay.requestCharacteristicUpdate(
                    BleDirDisplay.MODE_CHARACTERISTIC_UUID,
                    Mode.NOTHING
                )
                removeLocationUpdates()
            } else {
                dirDisplay.requestCharacteristicUpdate(
                    BleDirDisplay.MODE_CHARACTERISTIC_UUID,
                    Mode.NAVIGATION
                )
            }
            return false
        } else {
            if (!isRequestingLocationUpdates(this)) {
                requestLocationUpdates()
            }
            dirDisplay.requestCharacteristicUpdate(
                BleDirDisplay.MODE_CHARACTERISTIC_UUID,
                Mode.SPEEDOMETER
            )
            return true
        }
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: NavigationService
            get() = this@NavigationService
    }

    companion object {
        private const val PACKAGE_NAME = "com.example.bikenavigatorapp"
        private val TAG = "${NavigationService::class.java.simpleName}(bnalt)"

        /**
         * The name of the channel for notifications.
         */
        private const val CHANNEL_ID = "channel_01"

        private const val EXTRA_STARTED_LOC_SERVICE_FROM_NOTIFICATION =
            "$PACKAGE_NAME.started_from_notification"

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2

        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private const val NOTIFICATION_ID = 12345678
        private const val MPS_TO_KPH_COEFFICIENT = 3.6
    }
}