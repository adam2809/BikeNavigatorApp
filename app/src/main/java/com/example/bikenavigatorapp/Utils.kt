package com.example.bikenavigatorapp

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

val HTTPS_REGEX =
    Regex("(?:([A-Za-z]+):)?(\\/{0,3})([0-9.\\-A-Za-z]+)(?::(\\d+))?(?:\\/([^?#]*))?(?:\\?([^#]*))?(?:#(.*))?\$")

fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}