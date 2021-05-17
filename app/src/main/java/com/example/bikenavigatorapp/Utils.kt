package com.example.bikenavigatorapp

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

val HTTPS_REGEX =
    Regex("(?:([A-Za-z]+):)?(/{0,3})([0-9.\\-A-Za-z]+)(?::(\\d+))?(?:/([^?#]*))?(?:\\?([^#]*))?(?:#(.*))?\$")
val LOCATION_REGEX = Regex("!8m2!3d(\\d{1,2}\\.\\d+)!4d(\\d{1,2}\\.\\d+)?")

fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}