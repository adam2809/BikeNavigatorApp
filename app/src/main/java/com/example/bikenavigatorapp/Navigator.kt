package com.example.bikenavigatorapp

import android.location.Location
import android.util.Log
import kotlin.math.*

fun Location.toRadians(): Pair<Double, Double> {
    return Pair(latitude * Math.PI / 180, longitude * Math.PI / 180)
}


fun DirApi.Location.toRadians(): Pair<Double, Double> {
    return Pair(lat * Math.PI / 180, lng * Math.PI / 180)
}


fun DirApi.Location.distance(loc: Location): Double {
    val (f1, l1) = this.toRadians()
    val (f2, l2) = loc.toRadians()
    val (df, dl) = Pair(abs(f1 - f2), abs(l1 - l2))

    val a = sin(df / 2).pow(2) + cos(f1) * cos(f2) * sin(dl / 2).pow(2)
    val c = atan2(sqrt(a), sqrt(1 - a))

    return Navigator.EARTH_RADIUS_METERS * c
}

class Navigator(private val context: MainActivity) {
    companion object {
        const val WAYPOINT_RADIUS = 10F
        const val EARTH_RADIUS_METERS = 6371F * 1000F
        const val TAG = "Navigator"
    }

    var location: Location? = null
        set(value) {
            field = value
            update()
        }

    private fun update() {
        if (location == null) {
            return
        }
        findNearbyWaypoints(location!!).also { Log.i(TAG, "Found nearby waypoints: $it") }
    }

    private fun findNearbyWaypoints(loc: Location): Pair<List<DirApi.Step>, List<DirApi.Step>> {
        return Pair(
            context.dirs.steps.filter {
                it.startLocation.distance(loc).also { dist ->
                    Log.d(
                        TAG,
                        "Distance between $loc and $it is $dist"
                    )
                } < WAYPOINT_RADIUS
            },
            context.dirs.steps.filter {
                it.endLocation.distance(loc).also { dist ->
                    Log.d(
                        TAG,
                        "Distance between $loc and $it is $dist"
                    )
                } < WAYPOINT_RADIUS
            }
        )
    }
}