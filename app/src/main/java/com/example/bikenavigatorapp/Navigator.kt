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


fun DirApi.Location.distance(line: Navigator.Line): Double {
    line.perpendicular(
        BigDecimal(this.lat.toString()),
        BigDecimal(this.lng.toString())
    ).let {
        return this.distance(line.intersection(it))
    }
}


//TODO new maths dont work when for example there is a u-turn on a highway and two steps are parallel
class Navigator(
    private val steps: List<DirApi.Step>,
    startLocation: Location,
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

    init {
        Log.i(TAG, "Writing first step")
        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.DIR_CHARACTERISTIC_UUID,
            steps[1].toDir()
        )
        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.METERS_CHARACTERISTIC_UUID,
            steps[0].endLocation.distance(startLocation).toInt()
        )
        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.MODE_CHARACTERISTIC_UUID,
            BleDirDisplay.Mode.NAVIGATION
        )
    }

    private fun update() {
        if (location == null) {
            return
        }
        Log.d(TAG, "Location is = ${location?.latitude}  ${location?.longitude}")

        val currStep = findCurrStepIndex()

        val index = currStep?.index ?: run {
            Log.w(TAG, "Step $currStep does not have an index")
        }
        val dir: BleDirDisplay.Dir = when {
            currStep == null -> BleDirDisplay.Dir.NO_DIR
            index < steps.lastIndex -> steps[index + 1].toDir()
            else -> currStep.toDir()
        }

        Log.d(TAG, "Dir is $currStep")

        var meters: Int? = 0
        currStep?.let {
            meters = checkForNewMeters(it)
        } ?: run {
            Log.w(TAG, "Trying to set meters when curr step was not found")
        }

        meters?.let {
            Log.d(TAG, "Setting new meters=$it")
            dirDisplay.requestCharacteristicUpdate(
                BleDirDisplay.METERS_CHARACTERISTIC_UUID,
                it
            )
        }

        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.DIR_CHARACTERISTIC_UUID,
            dir
        )
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

    private fun checkForNewMeters(step: DirApi.Step): Int? {
        step.let {
            val currDistance = it.endLocation.distance(location!!)
            if (abs(
                    currDistance - (dirDisplay.currMeters)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                return currDistance.toInt()
            }
        }
        return null
    }

    data class Line(val inc: BigDecimal, val lng: BigDecimal) {
        constructor(
            aLat: BigDecimal, aLng: BigDecimal,
            bLat: BigDecimal, bLng: BigDecimal
        ) : this(
            (aLat - bLat) / (aLng - bLng),
            (bLat - (aLat - bLat) / (aLng - bLng) * bLng)
        )

        fun perpendicular(lat: BigDecimal, lng: BigDecimal): Line {
            (BigDecimal("1") / -inc).let { newInc ->
                return Line(newInc, lat - newInc * lng)
            }
        }

        fun value(lat: BigDecimal, lng: BigDecimal): BigDecimal {
            return lat - this.inc * lng - this.lng
        }

        fun intersection(line: Line): DirApi.Location {
            ((line.lng - this.lng) / (this.inc - line.inc)).let { retLat ->
                return DirApi.Location((this.inc * retLat + this.lng).toDouble(), retLat.toDouble())
            }
        }
    }

    private fun findCurrStepIndex(): DirApi.Step? {
        steps.filter {
            isPointBetweenPerpendicularLines(
                it.startLocation,
                it.endLocation,
                location?.toDirApiLocation() ?: run {
                    Log.e(TAG, "Trying to update location without set location")
                    DirApi.Location(0.0, 0.0)
                }
            )
        }.let {
            Log.d(
                TAG,
                "Filtered steps size is: ${it.size}, indexes=${it.map { step -> "${step.index}, " }}"
            )
            return when (it.size) {
                0 -> null
                1 -> it.first()
                else -> it.minByOrNull { step ->
                    val startLat = BigDecimal(step.startLocation.lat.toString())
                    val startLng = BigDecimal(step.startLocation.lng.toString())
                    val endLat = BigDecimal(step.endLocation.lat.toString())
                    val endLng = BigDecimal(step.endLocation.lng.toString())

                    val startEndLine = Line(startLat, startLng, endLat, endLng)
                    location!!.toDirApiLocation().distance(startEndLine)
                }
            }
        }
    }

    private fun isPointBetweenPerpendicularLines(
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
        val perpendicularToStartEndAtStart = lineStartEnd.perpendicular(startLat, startLng)
        val perpendicularToStartEndAtEnd = lineStartEnd.perpendicular(endLat, endLng)

        return isPointBetweenLines(
            perpendicularToStartEndAtStart, perpendicularToStartEndAtEnd,
            pointLat, pointLng,
            endLat, endLng,
            startLat, startLng
        )
    }

    private fun isPointBetweenLines(
        l: Line, k: Line,
        pointLat: BigDecimal, pointLng: BigDecimal,
        lLineRefLat: BigDecimal, lLineRefLng: BigDecimal,
        kLineRefLat: BigDecimal, kLineRefLng: BigDecimal
    ): Boolean {
        return l.value(pointLat, pointLng).signum() == l.value(lLineRefLat, lLineRefLng).signum() &&
                k.value(pointLat, pointLng).signum() == k.value(kLineRefLat, kLineRefLng).signum()
    }
}