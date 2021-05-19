package com.example.bikenavigatorapp

import android.location.Location
import android.util.Log
import kotlin.math.*
//TODO increase meters size only holding 255 meters for now
//TODO fix activity lifecycles - do not reinit bledirdisp since it breaks the gatt connection
//TODO ask for background location permissions
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

fun List<DirApi.Step>.leftShift(): List<DirApi.Step> {
    return this.windowed(2).map { (curr, next) ->
        curr.copy(
            maneuver = next.maneuver
        )
    }
}

class Navigator(private val context: MainActivity) {
    companion object {
        const val WAYPOINT_RADIUS = 10F
        const val EARTH_RADIUS_METERS = 6371F * 1000F
        const val TAG = "Navigator"
        const val METERS_DISPLAY_INTERVAL = 2
    }

    var location: Location? = null
        set(value) {
            field = value
            update()
        }
    var currStep: DirApi.Step? = null
    private var prevWaypoints: Pair<List<DirApi.Step>, List<DirApi.Step>>? = null


    private fun update() {
        if (location == null) {
            return
        }
        val (starts, ends) = findNearbyWaypoints(location!!)
        Log.d(
            TAG,
            "Found nearby waypoints: starts=${starts.map { it.startLocation.toString() }} ends=${ends.map { it.startLocation.toString() }}"
        )
        Log.d(TAG, "Current step = $currStep")

        var newDir: BleDirDisplay.Dir? = null
        var newMeters: Int? = null

        if (prevWaypoints?.second?.isEmpty() != false && ends.isNotEmpty()) {
            ends.getOrNull(0)?.let {
                if (currStep != null) {
                    Log.i(TAG, "Ending step: $currStep")
                    newDir = BleDirDisplay.Dir.NO_DIR
                    currStep = null
                }
            }
        }

        if (prevWaypoints?.first?.isEmpty() != false && starts.isNotEmpty()) {
            starts.getOrNull(0)?.let {
                if (currStep == null) {
                    Log.i(TAG, "Starting step: $it")
                    newDir = when (it.maneuver) {
                        "turn-left" -> BleDirDisplay.Dir.LEFT
                        "turn-right" -> BleDirDisplay.Dir.RIGHT
                        else -> BleDirDisplay.Dir.STRAIGHT
                    }
                    currStep = it
                }
            }
        }

        currStep?.let {
            val currDistance = it.endLocation.distance(location!!)
            if (abs(
                    currDistance - (context.dirDisplay.currDirData?.meters ?: Int.MAX_VALUE)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                newMeters = currDistance.toInt()
            }
        }


        if (newDir != null || newMeters != null) {
            context.dirDisplay.let {
                it.writeDir(BleDirDisplay.DirData(
                    newDir.let { newDir } ?: it.currDirData.dir,
                    newMeters.let { newMeters } ?: it.currDirData.meters
                ))
            }
        }

        prevWaypoints = Pair(starts, ends)
    }

    private fun findNearbyWaypoints(loc: Location): Pair<List<DirApi.Step>, List<DirApi.Step>> {
        return Pair(
            context.dirs.steps.leftShift().filter {
                it.startLocation.distance(loc) < WAYPOINT_RADIUS
            },
            context.dirs.steps.leftShift().filter {
                it.endLocation.distance(loc) < WAYPOINT_RADIUS
            }
        )
    }
}