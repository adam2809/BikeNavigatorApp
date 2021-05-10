package com.example.bikenavigatorapp

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes

object GeofenceErrorMessages {
    fun getErrorString(context: Context, e: Exception): String {
        return if (e is ApiException) {
            getErrorString(context, e.statusCode)
        } else {
            "R.string.geofence_unknown_error"
        }
    }

    fun getErrorString(context: Context, errorCode: Int): String {
        val resources = context.resources
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                "R.string.geofence_not_available"

            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                "R.string.geofence_too_many_geofences"

            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                "R.string.geofence_too_many_pending_intents"

            else -> "R.string.geofence_unknown_error"
        }
    }
}