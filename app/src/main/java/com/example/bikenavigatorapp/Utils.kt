package com.example.bikenavigatorapp

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DateFormat
import java.util.*


val SHARE_PLACE_URL_REGEX = Regex("https://maps\\.app\\.goo\\.gl/\\w+")
val DESTINATION_LOCATION_IN_DATA_PARAM_REGEX =
    Regex("!8m2!3d(\\d{1,2}\\.\\d+)!4d(\\d{1,2}\\.\\d+)?")
val DESTINATION_LOCATION_IN_PATH_REGEX =
    Regex("https://www.google.com/maps/dir/\\d{1,2}\\.\\d+,\\d{1,2}\\.\\d+/(\\d{1,2}\\.\\d+),(\\d{1,2}\\.\\d+)/")
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
fun isRequestingLocationUpdates(context: Context?): Boolean {
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

fun Location.toDirApiLocation(): Loc {
    return Loc(this.latitude, this.longitude)
}

fun ByteArray.toInt(): Int {
    if (this.size > 4) {
        throw IllegalArgumentException("Byte array is too big")
    }
    var acc = 0
    this.forEachIndexed { i, it ->
        acc = acc or (it.toInt() shl 8 * i)
    }
    return acc
}

fun Int.toByteArray(): ByteArray {
    return byteArrayOf(
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        (this shr 0).toByte()
    )
}

fun itNotBeZero(it: BigDecimal) = if (it.toBigInteger() == BigInteger("0")) BigDecimal(
    0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001
) else it