package com.example.lib

import java.math.BigDecimal
import kotlin.math.*


fun isPointBetweenLines(
    l: Line, k: Line,
    pointLat: BigDecimal, pointLng: BigDecimal,
    refLat: BigDecimal, refLng: BigDecimal
): Boolean {
    return isPointBetweenLines(
        l, k,
        pointLat, pointLng,
        refLat, refLng,
        refLat, refLng
    )
}


val EARTH_RADIUS_METERS: Double = 6371008.8
val METERS_TO_ARC_COEFF = BigDecimal("0.000008999280057498208")
val CORRIDOR_WIDTH = BigDecimal("200")
val WAYPOINT_RADIUS: Double = 10.0

data class Location(val lat: Double, val lng: Double)

fun Location.toRadians(): Pair<Double, Double> {
    return Pair(lat * Math.PI / 180, lng * Math.PI / 180)
}


fun Location.distance(loc: Location): Double {
    val (f1, l1) = this.toRadians()
    val (f2, l2) = loc.toRadians()
    val (df, dl) = Pair(abs(f1 - f2), abs(l1 - l2))

    val a = sin(df / 2).pow(2) + cos(f1) * cos(f2) * sin(dl / 2).pow(2)
    val c = atan2(sqrt(a), sqrt(1 - a))

    return EARTH_RADIUS_METERS * c
}

fun Location.distance(line: Line): Double {
    line.perpendicular(
        BigDecimal(this.lat.toString()),
        BigDecimal(this.lng.toString())
    ).let {
        return this.distance(line.intersection(it))
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

    fun intersection(line: Line): Location {
        ((line.lng - this.lng) / (this.inc - line.inc)).let { retLat ->
            return Location((this.inc * retLat + this.lng).toDouble(), retLat.toDouble())
        }
    }
}


fun isPointBetweenPerpendicularLines(
    start: Location,
    end: Location,
    point: Location
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


fun isPointBetweenLines(
    l: Line, k: Line,
    pointLat: BigDecimal, pointLng: BigDecimal,
    lLineRefLat: BigDecimal, lLineRefLng: BigDecimal,
    kLineRefLat: BigDecimal, kLineRefLng: BigDecimal
): Boolean {
    return l.value(pointLat, pointLng).signum() == l.value(lLineRefLat, lLineRefLng).signum() &&
            k.value(pointLat, pointLng).signum() == k.value(kLineRefLat, kLineRefLng).signum()
}

fun main(arr: Array<String>) {

// santa monica blvd/n maple drive
    val start = Location(52.2245923, 21.0949837)

// santa monica blvd/rodeo drive
    val end = Location(52.2230988, 21.1012119)

    val curr = Location(52.223490, 21.099493)


    val startLat = BigDecimal(start.lat.toString())
    val startLng = BigDecimal(start.lng.toString())
    val endLat = BigDecimal(end.lat.toString())
    val endLng = BigDecimal(end.lng.toString())

    val lineStartEnd = Line(
        startLat, startLng,
        endLat, endLng
    )

    println(isPointBetweenPerpendicularLines(start, end, curr))
}