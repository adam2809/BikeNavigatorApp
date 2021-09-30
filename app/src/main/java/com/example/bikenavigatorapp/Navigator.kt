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
        val dir: Dir = when {
            index < steps.lastIndex -> steps[index + 1].toDir()
            else -> currStep.toDir()
        }

        Log.d(TAG, "Dir is $dir")

        var meters: Int? = 0
        currStep.let {
            meters = checkForNewMeters(it)
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