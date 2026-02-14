package com.example.pitwise.domain.map

import kotlin.math.*

/**
 * Coordinate conversion utilities.
 * Supports: UTM, Lat/Lng decimal, DMS (Degrees Minutes Seconds).
 */

enum class CoordinateFormat { UTM, LAT_LNG, DMS }

data class UtmCoordinate(
    val zone: Int,
    val letter: Char,
    val easting: Double,
    val northing: Double
) {
    fun format(): String {
        val e = "%.0f".format(easting)
        val n = "%.0f".format(northing)
        return "$zone $letter $e E $n N"
    }
}

object CoordinateUtils {

    /**
     * Convert WGS84 lat/lng to UTM.
     * Uses standard Transverse Mercator projection formulas.
     */
    fun latLngToUtm(lat: Double, lng: Double): UtmCoordinate {
        val zone = ((lng + 180.0) / 6.0).toInt() + 1
        val letter = utmLetterDesignator(lat)

        val a = 6378137.0          // WGS84 semi-major axis
        val f = 1 / 298.257223563  // WGS84 flattening
        val e2 = 2 * f - f * f     // first eccentricity squared
        val ep2 = e2 / (1 - e2)    // second eccentricity squared

        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)

        val lngOrigin = (zone - 1) * 6 - 180 + 3  // central meridian
        val lngOriginRad = Math.toRadians(lngOrigin.toDouble())

        val N = a / sqrt(1 - e2 * sin(latRad).pow(2))
        val T = tan(latRad).pow(2)
        val C = ep2 * cos(latRad).pow(2)
        val A = cos(latRad) * (lngRad - lngOriginRad)

        val M = a * (
                (1 - e2 / 4 - 3 * e2.pow(2) / 64 - 5 * e2.pow(3) / 256) * latRad
                        - (3 * e2 / 8 + 3 * e2.pow(2) / 32 + 45 * e2.pow(3) / 1024) * sin(2 * latRad)
                        + (15 * e2.pow(2) / 256 + 45 * e2.pow(3) / 1024) * sin(4 * latRad)
                        - (35 * e2.pow(3) / 3072) * sin(6 * latRad)
                )

        val k0 = 0.9996

        var easting = k0 * N * (
                A + (1 - T + C) * A.pow(3) / 6
                        + (5 - 18 * T + T.pow(2) + 72 * C - 58 * ep2) * A.pow(5) / 120
                ) + 500000.0

        var northing = k0 * (
                M + N * tan(latRad) * (
                A.pow(2) / 2 + (5 - T + 9 * C + 4 * C.pow(2)) * A.pow(4) / 24
                        + (61 - 58 * T + T.pow(2) + 600 * C - 330 * ep2) * A.pow(6) / 720
                )
                )

        if (lat < 0) {
            northing += 10000000.0  // offset for southern hemisphere
        }

        return UtmCoordinate(zone, letter, easting, northing)
    }

    /**
     * Format as decimal lat/lng.
     * Example: "-0.661668, 114.975332"
     */
    fun formatLatLng(lat: Double, lng: Double): String {
        return "%.6f, %.6f".format(lat, lng)
    }

    /**
     * Format as DMS with hemisphere.
     * Example: "0°39'42.0" S, 114°58'31.2" E"
     */
    fun formatDms(lat: Double, lng: Double): String {
        val latHemi = if (lat >= 0) "N" else "S"
        val lngHemi = if (lng >= 0) "E" else "W"
        return "${toDms(abs(lat))} $latHemi, ${toDms(abs(lng))} $lngHemi"
    }

    /**
     * Format coordinate based on the selected format.
     */
    fun format(lat: Double, lng: Double, format: CoordinateFormat): String {
        return when (format) {
            CoordinateFormat.UTM -> latLngToUtm(lat, lng).format()
            CoordinateFormat.LAT_LNG -> formatLatLng(lat, lng)
            CoordinateFormat.DMS -> formatDms(lat, lng)
        }
    }

    private fun toDms(decimal: Double): String {
        val deg = decimal.toInt()
        val minFull = (decimal - deg) * 60
        val min = minFull.toInt()
        val sec = (minFull - min) * 60
        return "%d°%02d'%04.1f\"".format(deg, min, sec)
    }

    private fun utmLetterDesignator(lat: Double): Char {
        return when {
            lat >= 72 -> 'X'
            lat >= 64 -> 'W'
            lat >= 56 -> 'V'
            lat >= 48 -> 'U'
            lat >= 40 -> 'T'
            lat >= 32 -> 'S'
            lat >= 24 -> 'R'
            lat >= 16 -> 'Q'
            lat >= 8  -> 'P'
            lat >= 0  -> 'N'
            lat >= -8 -> 'M'
            lat >= -16 -> 'L'
            lat >= -24 -> 'K'
            lat >= -32 -> 'J'
            lat >= -40 -> 'H'
            lat >= -48 -> 'G'
            lat >= -56 -> 'F'
            lat >= -64 -> 'E'
            lat >= -72 -> 'D'
            lat >= -80 -> 'C'
            else -> 'Z'
        }
    }
}
