package com.example.bikenavigatorapp

import android.location.Location
import android.util.Log
import java.math.BigDecimal
import kotlin.math.abs


//TODO new maths dont work when for example there is a u-turn on a highway and two steps are parallel
class Navigator(
    private val steps: List<Step>,
    startLocation: Location,
    private val dirDisplay: BleDirDisplay
) {
    companion object {
        const val WAYPOINT_RADIUS = 10F
        const val EARTH_RADIUS_METERS = 6371F * 1000F
        private val TAG = "${Navigator::class.java.simpleName}(bnalt)"
        const val METERS_DISPLAY_INTERVAL = 2
    }

    var currStep: Step


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
            steps[0].endLoc.distance(startLocation).toInt()
        )
        dirDisplay.requestCharacteristicUpdate(
            BleDirDisplay.MODE_CHARACTERISTIC_UUID,
            Mode.NAVIGATION
        )

        currStep = steps[0]
    }

    private fun update() {
        if (location == null) {
            return
        }
        Log.d(TAG, "Location is = ${location?.latitude}  ${location?.longitude}")

        val nextStep = findNextStep()
        nextStep?.let {
            currStep = nextStep
        } ?: run {
            Log.d(TAG, "Could not find next step cleaving curr step the same")
        }

        val index = currStep.index ?: run {
            Log.w(TAG, "Step $currStep does not have an index")
        }


        val dir: Dir = if (index == steps.lastIndex) {
            Dir.FINISH
        } else {
            steps[index + 1].toDir().let {
                if (it == Dir.ROUNDABOUT_LEFT || it == Dir.ROUNDABOUT_RIGHT)
                    resolveRoundaboutDirection(currStep,
                        steps[(currStep.index
                            ?: throw IndexOutOfBoundsException("Step does not have an index assigned")) + 1]
                    )
                else
                    it
            }
        }
        Log.d(TAG, "Step is $currStep")
        Log.d(TAG, "Dir is $dir")

        checkForNewMeters(currStep)?.let {
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

    private fun checkForNewMeters(step: Step): Int? {
        step.let {
            val currDistance = it.endLoc.distance(location!!)
            if (abs(
                    currDistance - (dirDisplay.currMeters)
                ) > METERS_DISPLAY_INTERVAL
            ) {
                return currDistance.toInt()
            }
        }
        return null
    }

    private fun findNextStep(): Step? {
        steps.filter {
            isPointBetweenPerpendicularLines(
                it.startLoc,
                it.endLoc,
                location?.toDirApiLocation() ?: run {
                    Log.e(TAG, "Trying to update location without set location")
                    Loc(0.0, 0.0)
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
                    val startLat = BigDecimal(step.startLoc.lat.toString())
                    val startLng = BigDecimal(step.startLoc.lng.toString())
                    val endLat = BigDecimal(step.endLoc.lat.toString())
                    val endLng = BigDecimal(step.endLoc.lng.toString())

                    val startEndLine = Line(startLat, startLng, endLat, endLng)
                    location!!.toDirApiLocation().distance(startEndLine)
                }
            }
        }
    }

    private fun isPointBetweenPerpendicularLines(
        start: Loc,
        end: Loc,
        point: Loc
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

fun Int.notNegative(): Int = if (this < 0) 0 else this
fun resolveRoundaboutDirection(beforeRoundabout: Step, afterRoundabout: Step): Dir {
    val lineBeforeRoundabout = Line(beforeRoundabout.startLoc, beforeRoundabout.endLoc)
    val lineAfterRoundabout = Line(afterRoundabout.startLoc, afterRoundabout.endLoc)
    val lineStartEnd = Line(beforeRoundabout.startLoc, afterRoundabout.endLoc)

    val valueOfBeforeAtAfterEnd =
        lineBeforeRoundabout.value(afterRoundabout.endLoc).signum().notNegative()
    val valueOfAfterAtBeforeStart =
        lineAfterRoundabout.value(beforeRoundabout.startLoc).signum().notNegative()
    val valueOfStartEndAtBeforeEnd =
        lineStartEnd.value(beforeRoundabout.endLoc).signum().notNegative()
    val bitmap =
        (valueOfBeforeAtAfterEnd shl 2) or (valueOfAfterAtBeforeStart shl 1) or valueOfStartEndAtBeforeEnd

    return if (((bitmap shr 2) == 0 || bitmap == 5) && bitmap != 2) Dir.ROUNDABOUT_LEFT else Dir.ROUNDABOUT_RIGHT
}