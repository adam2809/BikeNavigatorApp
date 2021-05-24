package com.example.bikenavigatorapp

import android.location.Location
import android.util.Log
import kotlin.math.*
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
    private var writeSuccessful = false


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

        val newDir: BleDirDisplay.Dir? = checkForNewDir(starts, ends)
        val newMeters: Int? = checkForNewMeters()

        if (newDir != null || newMeters != null || !writeSuccessful) {
            Log.d(
                TAG,
                "Setting new ${newDir?.let { "dir=$it" } ?: ""} ${newMeters?.let { "meters=$it" } ?: ""}")
            writeDirData(newDir, newMeters)
        }

        prevWaypoints = Pair(starts, ends)
    }

    private fun writeDirData(dir: BleDirDisplay.Dir?, meters: Int?) {
        context.dirDisplay.let {
            writeSuccessful = it.writeDir(BleDirDisplay.DirData(
                dir.let { dir } ?: it.currDirData.dir,
                meters.let { meters } ?: it.currDirData.meters
            ))
        }
    }

    private fun findNearbyWaypoints(loc: Location): Pair<List<DirApi.Step>, List<DirApi.Step>> {
        return Pair(
            context.dirs.steps.filter {
                it.startLocation.distance(loc) < WAYPOINT_RADIUS
            },
            context.dirs.steps.filter {
                it.endLocation.distance(loc) < WAYPOINT_RADIUS
            }
        )
    }

    private fun checkForNewDir(starts:List<DirApi.Step>,ends:List<DirApi.Step>):BleDirDisplay.Dir?{
        val (prevStarts, prevEnds) = prevWaypoints ?: Pair(null, null)
        var ret:BleDirDisplay.Dir? = null

        if (prevEnds.isNullOrEmpty() && ends.isNotEmpty()) {
            ends.first().let {
                if (currStep != null) {
                    Log.i(TAG, "Ending step: $currStep")
                    ret = BleDirDisplay.Dir.NO_DIR
                    currStep = null
                }
            }
        }

        if (prevStarts.isNullOrEmpty() && starts.isNotEmpty()) {
            starts.first().let {
                if (currStep == null) {
                    Log.i(TAG, "Starting step: $it")
                    ret = when (it.maneuver) {
                        "turn-left" -> BleDirDisplay.Dir.LEFT
                        "turn-right" -> BleDirDisplay.Dir.RIGHT
                        else -> BleDirDisplay.Dir.STRAIGHT
                    }
                    currStep = it
                }
            }
        }

        return ret
    }

    private fun checkForNewMeters():Int?{
        currStep?.let {
            val currDistance = it.endLocation.distance(location!!)
            if (abs(
                    currDistance - (context.dirDisplay.currDirData.meters)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                return currDistance.toInt()
            }
        }
        return null
    }
}