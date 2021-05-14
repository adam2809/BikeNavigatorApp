package com.example.bikenavigatorapp.geofencing

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.example.bikenavigatorapp.BleDirDisplay
import com.google.android.gms.location.GeofencingEvent

class GeofenceTransitionsJobIntentService : JobIntentService() {

    companion object{
        const val TAG= "JobIntentService";
        private const val JOB_ID = 573

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java, JOB_ID,
                intent)
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
            "This ${geofencingEvent.triggeringLocation.latitude}, ${geofencingEvent.triggeringLocation.longitude} (${geofencingEvent.geofenceTransition}) should be handled here"
        )
//        Intent().also {
//            it.action = "DISPLAY_DIR_CHANGE"
//            it.putExtra("dir", BleDirDisplay.Dir.STRAIGHT.toString())
//            sendBroadcast(intent)
//        }
    }
}