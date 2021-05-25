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
    var writeFirstStep = false

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

        val isStepUpdate = updateCurrStep(starts, ends)
        val index = currStep?.index?.plus(1)
        val newDir: BleDirDisplay.Dir? = when {
            !isStepUpdate -> null
            index != null && index < context.dirs.steps.size -> context.dirs.steps[index].toDir()
            currStep == null -> BleDirDisplay.Dir.NO_DIR
            else -> currStep?.toDir()
        }

        val newMeters: Int? = checkForNewMeters()

        if (newDir != null || newMeters != null || !writeSuccessful) {
            Log.d(
                TAG,
                "Setting new ${newDir?.let { "dir=$it" } ?: ""} ${newMeters?.let { "meters=$it" } ?: ""}")
            writeDirData(newDir, newMeters)
            writeFirstStep = false
        }

        if (writeFirstStep) {
            Log.i(TAG, "Writing first step")
            context.dirs.steps.let {
                currStep = it[0]
                writeDirData(it[1].toDir(), it[1].endLocation.distance(location!!).toInt())
            }
            writeFirstStep = false
        }

        prevWaypoints = Pair(starts, ends)
    }

    private fun writeDirData(dir: BleDirDisplay.Dir?, meters: Int?) {
        context.dirDisplay.let {
            writeSuccessful = it.writeDir(BleDirDisplay.DirData(
                dir.let { dir } ?: it.targetDirData.dir,
                meters.let { meters } ?: it.targetDirData.meters
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

    private fun updateCurrStep(starts: List<DirApi.Step>, ends: List<DirApi.Step>): Boolean {
        val (prevStarts, prevEnds) = prevWaypoints ?: Pair(null, null)
        var ret = false

        if (prevEnds.isNullOrEmpty() && ends.isNotEmpty()) {
            ends.first().let {
                if (currStep != null) {
                    Log.i(TAG, "Ending step: $currStep")
                    currStep = null
                    ret = true
                }
            }
        }

        if (prevStarts.isNullOrEmpty() && starts.isNotEmpty()) {
            starts.first().let {
                if (currStep == null) {
                    Log.i(TAG, "Starting step: $it")
                    currStep = it
                    ret = true
                }
            }
        }
        return ret
    }

    private fun DirApi.Step.toDir(): BleDirDisplay.Dir {
        return when (this.maneuver) {
            "turn-left" -> BleDirDisplay.Dir.LEFT
            "turn-right" -> BleDirDisplay.Dir.RIGHT
            else -> BleDirDisplay.Dir.STRAIGHT
        }
    }

    private fun checkForNewMeters(): Int? {
        currStep?.let {
            val currDistance = it.endLocation.distance(location!!)
            if (abs(
                    currDistance - (context.dirDisplay.targetDirData.meters)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                return currDistance.toInt()
            }
        }
        return null
    }
}