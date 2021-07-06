package com.example.bikenavigatorapp

import android.location.Location
import android.util.Log
import java.math.BigDecimal
import kotlin.math.*

fun DirApi.Location.toRadians(): Pair<Double, Double> {
    return Pair(lat * Math.PI / 180, lng * Math.PI / 180)
}


fun DirApi.Location.distance(loc: DirApi.Location): Double {
    val (f1, l1) = this.toRadians()
    val (f2, l2) = loc.toRadians()
    val (df, dl) = Pair(abs(f1 - f2), abs(l1 - l2))

    val a = sin(df / 2).pow(2) + cos(f1) * cos(f2) * sin(dl / 2).pow(2)
    val c = atan2(sqrt(a), sqrt(1 - a))

    return Navigator.EARTH_RADIUS_METERS * c
}

fun DirApi.Location.distance(loc: Location): Double {
    return this.distance(DirApi.Location(loc.latitude, loc.longitude))
}

class Navigator(
    private val steps: List<DirApi.Step>,
    private val startLocation: Location,
    private val dirDisplay: BleDirDisplay
) {
    companion object {
        const val WAYPOINT_RADIUS = 10F
        const val EARTH_RADIUS_METERS = 6371F * 1000F
        private val TAG = "${Navigator::class.java.simpleName}(bnalt)"
        const val METERS_DISPLAY_INTERVAL = 2
    }


    var location: Location? = null
        set(value) {
            field = value
            update()
        }
    private var step: DirApi.Step? = null
    private var prevWaypoints: Pair<List<DirApi.Step>, List<DirApi.Step>>? = null

    init {
        Log.i(TAG, "Writing first step")
        dirDisplay.targetDirData = dirDisplay.targetDirData.copy(
            dir = steps[1].toDir(),
            meters = steps[0].endLocation.distance(startLocation).toInt(),
            mode = BleDirDisplay.Mode.NAVIGATION
        )
    }

    private fun update() {
        if (location == null) {
            return
        }
        Log.d(TAG, "Location is = ${location?.latitude}  ${location?.longitude}")


        val meters: Int? = checkForNewMeters()

//        TODO meters of targetDirData and dir of targetDirData should be set at the same time this causes the deleay in changing dir when finishing and starting step
        meters?.let {
            Log.d(TAG, "Setting new meters=$it")
            dirDisplay.targetDirData = dirDisplay.targetDirData.copy(
                meters = it
            )
        }

        val (starts, ends) = findNearbyWaypoints(location!!)
        if (updateStepByWaypoints(starts, ends)) {
            Log.d(TAG, "By waypoint step search succeeded")
        }


        if (checkStepByBounds()) {
            Log.d(TAG, "By bounds step search succeeded")
        }

        Log.d(TAG, "Current step = $step")

        val index = step?.index ?: run {
            Log.w(TAG, "Step $step does not have an index")
        }
        val dir: BleDirDisplay.Dir = when {
            step == null -> BleDirDisplay.Dir.NO_DIR
            index < steps.lastIndex -> steps[index + 1].toDir()
            else -> step?.toDir() ?: BleDirDisplay.Dir.NO_DIR
        }
        Log.d(TAG, "Dir is $dir")

        dirDisplay.targetDirData = dirDisplay.targetDirData.copy(dir = dir)

        prevWaypoints = Pair(starts, ends)
    }

    private fun findNearbyWaypoints(loc: Location): Pair<List<DirApi.Step>, List<DirApi.Step>> {
        return Pair(
            steps.filter {
                it.startLocation.distance(loc) < WAYPOINT_RADIUS
            },
            steps.filter {
                it.endLocation.distance(loc) < WAYPOINT_RADIUS
            }
        )
    }

    private fun updateStepByWaypoints(starts: List<DirApi.Step>, ends: List<DirApi.Step>): Boolean {
        val (prevStarts, prevEnds) = prevWaypoints ?: Pair(null, null)
        var ret = false

        if (prevEnds.isNullOrEmpty() && ends.isNotEmpty()) {
            ends.first().let {
                if (step != null) {
                    Log.i(TAG, "Ending step: $step")
                    step = null
                    ret = true
                }
            }
        }

        if (prevStarts.isNullOrEmpty() && starts.isNotEmpty()) {
            starts.first().let {
                if (step == null) {
                    Log.i(TAG, "Starting step: $it")
                    step = it
                    ret = true
                }
            }
        }
        return ret
    }

    private fun DirApi.Step.toDir(): BleDirDisplay.Dir {
        return when (this.maneuver) {
            "turn-sharp-left" -> BleDirDisplay.Dir.TURN_SHARP_LEFT
            "uturn-right" -> BleDirDisplay.Dir.UTURN_RIGHT
            "turn-slight-right" -> BleDirDisplay.Dir.TURN_SLIGHT_RIGHT
            "merge" -> BleDirDisplay.Dir.MERGE
            "roundabout-left" -> BleDirDisplay.Dir.ROUNDABOUT_LEFT
            "roundabout-right" -> BleDirDisplay.Dir.ROUNDABOUT_RIGHT
            "uturn-left" -> BleDirDisplay.Dir.UTURN_LEFT
            "turn-slight-left" -> BleDirDisplay.Dir.TURN_SLIGHT_LEFT
            "turn-left" -> BleDirDisplay.Dir.TURN_LEFT
            "ramp-right" -> BleDirDisplay.Dir.RAMP_RIGHT
            "turn-right" -> BleDirDisplay.Dir.TURN_RIGHT
            "fork-right" -> BleDirDisplay.Dir.FORK_RIGHT
            "straight" -> BleDirDisplay.Dir.STRAIGHT
            "fork-left" -> BleDirDisplay.Dir.FORK_LEFT
            "ferry-train" -> BleDirDisplay.Dir.FERRY_TRAIN
            "turn-sharp-right" -> BleDirDisplay.Dir.TURN_SHARP_RIGHT
            "ramp-left" -> BleDirDisplay.Dir.RAMP_LEFT
            "ferry" -> BleDirDisplay.Dir.FERRY
            else -> BleDirDisplay.Dir.NO_DIR
        }
    }

    private fun checkForNewMeters(): Int? {
        step?.let {
            val currDistance = it.endLocation.distance(location!!)
            if (abs(
                    currDistance - (dirDisplay.targetDirData.meters)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                return currDistance.toInt()
            }
        }
        return null
    }

    private val CORRIDOR_WIDTH = BigDecimal("100")
    private val METERS_TO_ARC_COEFF = BigDecimal("0.000008999280057498208")

    data class Line(val inc: BigDecimal, val lng: BigDecimal) {
        constructor(
            aLat: BigDecimal, aLng: BigDecimal,
            bLat: BigDecimal, bLng: BigDecimal
        ) : this(
            (aLat - bLat) / (aLng - bLng),
            (bLat - (aLat - bLat) / (aLng - bLng) * bLng)
        )

        fun value(lat: BigDecimal, lng: BigDecimal): BigDecimal {
            return lat - this.inc * lng - this.lng
        }
    }

    private fun checkStepByBounds(): Boolean {
        steps.reversed().forEach {
            val loc = location?.let { l ->
                DirApi.Location(l.latitude, l.longitude)
            } ?: return false
            if (isPointInBounds(it.startLocation, it.endLocation, loc)) {
                step = it
                return true
            }
        }
        return false;
    }

    private fun isPointInBounds(
        start: DirApi.Location,
        end: DirApi.Location,
        point: DirApi.Location
    ): Boolean {
        val startLat = BigDecimal(start.lat.toString())
        val startLng = BigDecimal(start.lng.toString())
        val endLat = BigDecimal(end.lat.toString())
        val endLng = BigDecimal(end.lng.toString())
        val pointLat = BigDecimal(point.lat.toString())
        val pointLng = BigDecimal(point.lng.toString())

        val lineStartEnd = Line(
            startLat, startLng,
            endLat, endLng
        );

        val boundLine1 = Line(
            lineStartEnd.inc,
            lineStartEnd.lng + (CORRIDOR_WIDTH / BigDecimal("2")) * METERS_TO_ARC_COEFF
        );
        val boundLine2 = Line(
            lineStartEnd.inc,
            lineStartEnd.lng - (CORRIDOR_WIDTH / BigDecimal("2")) * METERS_TO_ARC_COEFF
        );

        val isPointBetweenStartAndEnd = isPointBetweenLines(
            boundLine1, boundLine2,
            pointLat, pointLng,
            startLat, startLng
        ) &&
                start.distance(end).let {
                    point.distance(start) <= it && point.distance(end) <= it
                }

        return (isPointBetweenStartAndEnd || point.distance(start) < WAYPOINT_RADIUS) && point.distance(
            end
        ) >= WAYPOINT_RADIUS
    }

    private fun isPointBetweenLines(
        l: Line, k: Line,
        pointLat: BigDecimal, pointLng: BigDecimal,
        refLat: BigDecimal, refLng: BigDecimal
    ): Boolean {
        return l.value(pointLat, pointLng).signum() == l.value(refLat, refLng).signum() &&
                k.value(pointLat, pointLng).signum() == k.value(refLat, refLng).signum()
    }

}