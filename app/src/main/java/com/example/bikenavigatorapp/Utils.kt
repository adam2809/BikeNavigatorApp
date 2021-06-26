package com.example.bikenavigatorapp

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.*


val SHARE_PLACE_URL_REGEX = Regex("https://maps\\.app\\.goo\\.gl/\\w+")
val DESTINATION_LOCATION_REGEX = Regex("!8m2!3d(\\d{1,2}\\.\\d+)!4d(\\d{1,2}\\.\\d+)?")
val ROUTE_INDEX_DATA_REGEX = Regex("!3e1!(\\w*)\\?utm_source=mstt_0")

fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}


const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"

/**
 * Returns true if requesting location updates, otherwise returns false.
 *
 * @param context The [Context].
 */
fun requestingLocationUpdates(context: Context?): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
}

/**
 * Stores the location updates state in SharedPreferences.
 * @param requestingLocationUpdates The location updates state.
 */
fun setRequestingLocationUpdates(context: Context?, requestingLocationUpdates: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
        .apply()
}

/**
 * Returns the `location` object as a human readable string.
 * @param location  The [Location].
 */
fun getLocationText(location: Location?): String {
    return if (location == null)
        "Unknown location"
    else
        "(${location.latitude}, ${location.longitude}"
}

fun getLocationTitle(context: Context): String? {
    return context.getString(
        R.string.location_updated,
        DateFormat.getDateTimeInstance().format(Date())
    )
}

fun Location.toDirApiLocation(): DirApi.Location {
    return DirApi.Location(this.latitude, this.longitude)
}