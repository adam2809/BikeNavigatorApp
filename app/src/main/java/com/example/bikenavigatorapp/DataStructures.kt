package com.example.bikenavigatorapp

import android.location.Location
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import kotlin.math.*


enum class Dir {
    NO_DIR,
    TURN_SHARP_LEFT,
    UTURN_RIGHT,
    TURN_SLIGHT_RIGHT,
    MERGE,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    UTURN_LEFT,
    TURN_SLIGHT_LEFT,
    TURN_LEFT,
    RAMP_RIGHT,
    TURN_RIGHT,
    FORK_RIGHT,
    STRAIGHT,
    FORK_LEFT,
    FERRY_TRAIN,
    TURN_SHARP_RIGHT,
    RAMP_LEFT,
    FERRY,
    FINISH
}

enum class Mode {
    NOTHING,
    NAVIGATION,
    SPEEDOMETER
}


data class TextVal(val text: String, val value: Int)
data class Loc(val lat: Double, val lng: Double) {
    private fun toRadians(): Pair<Double, Double> {
        return Pair(lat * Math.PI / 180, lng * Math.PI / 180)
    }


    private fun distance(loc: Loc): Double {
        val (f1, l1) = this.toRadians()
        val (f2, l2) = loc.toRadians()
        val (df, dl) = Pair(abs(f1 - f2), abs(l1 - l2))

        val a = sin(df / 2).pow(2) + cos(f1) * cos(f2) * sin(dl / 2).pow(2)
        val c = atan2(sqrt(a), sqrt(1 - a))

        return Navigator.EARTH_RADIUS_METERS * c
    }

    fun distance(loc: Location): Double {
        return this.distance(Loc(loc.latitude, loc.longitude))
    }


    fun distance(line: Line): Double {
        line.perpendicular(
            BigDecimal(this.lat.toString()),
            BigDecimal(this.lng.toString())
        ).let {
            return this.distance(line.intersection(it))
        }
    }
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

    fun intersection(line: Line): Loc {
        ((line.lng - this.lng) / (this.inc - line.inc)).let { retLat ->
            return Loc((this.inc * retLat + this.lng).toDouble(), retLat.toDouble())
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Step(
    @JsonIgnore
    var index: Int?,
    val distance: TextVal,
    val duration: TextVal,
    val maneuver: String?,
    @JsonProperty("start_location")
    val startLoc: Loc,
    @JsonProperty("end_location")
    val endLoc: Loc,
    @JsonProperty("html_instructions")
    val htmlInstructions: String,
    @JsonProperty("travel_mode")
    val travelMode: String
) {
    fun toDir(): Dir {
        return when (this.maneuver) {
            "turn-sharp-left" -> Dir.TURN_SHARP_LEFT
            "uturn-right" -> Dir.UTURN_RIGHT
            "turn-slight-right" -> Dir.TURN_SLIGHT_RIGHT
            "merge" -> Dir.MERGE
            "roundabout-left" -> Dir.ROUNDABOUT_LEFT
            "roundabout-right" -> Dir.ROUNDABOUT_RIGHT
            "uturn-left" -> Dir.UTURN_LEFT
            "turn-slight-left" -> Dir.TURN_SLIGHT_LEFT
            "turn-left" -> Dir.TURN_LEFT
            "ramp-right" -> Dir.RAMP_RIGHT
            "turn-right" -> Dir.TURN_RIGHT
            "fork-right" -> Dir.FORK_RIGHT
            "straight" -> Dir.STRAIGHT
            "fork-left" -> Dir.FORK_LEFT
            "ferry-train" -> Dir.FERRY_TRAIN
            "turn-sharp-right" -> Dir.TURN_SHARP_RIGHT
            "ramp-left" -> Dir.RAMP_LEFT
            "ferry" -> Dir.FERRY
            else -> Dir.NO_DIR
        }
    }
}