package com.geostamp.camera.data

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class CoordinateFormat(override val label: String) : Labeled {
    DECIMAL("Decimal Degrees (28.6139° N, 77.2090° E)"),
    DMS("Degrees Minutes Seconds (28°36'50\" N, 77°12'32\" E)"),
    UTM("Universal Transverse Mercator (43R 716000m E)"),
}

object CoordinateConverter {

    /** Formats coordinates according to [format]. */
    fun format(lat: Double, lon: Double, format: CoordinateFormat): String = when (format) {
        CoordinateFormat.DECIMAL -> formatDecimal(lat, lon)
        CoordinateFormat.DMS -> formatDms(lat, lon)
        CoordinateFormat.UTM -> toUtm(lat, lon)
    }

    fun formatDecimal(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "Lat %.6f° %s Long %.6f° %s", abs(lat), latDir, abs(lon), lonDir)
    }

    fun formatDms(lat: Double, lon: Double): String {
        fun dms(v: Double, pos: Char, neg: Char): String {
            val d = abs(v)
            val deg = floor(d).toInt()
            val remM = (d - deg) * 60.0
            val min = floor(remM).toInt()
            val sec = (remM - min) * 60.0
            val dir = if (v >= 0) pos else neg
            return String.format(Locale.US, "%d°%02d'%04.1f\"%c", deg, min, sec, dir)
        }
        return "${dms(lat, 'N', 'S')} ${dms(lon, 'E', 'W')}"
    }

    /**
     * Converts WGS84 Lat/Lon to Universal Transverse Mercator (UTM) coordinates.
     * Output format: `<zone><band> <easting>m E <northing>m N` (e.g. `43R 716123m E 3167456m N`).
     */
    fun toUtm(latitude: Double, longitude: Double): String {
        val lat = latitude.coerceIn(-80.0, 84.0)
        var lon = longitude
        while (lon < -180.0) lon += 360.0
        while (lon >= 180.0) lon -= 360.0

        val zone = floor((lon + 180.0) / 6.0).toInt() + 1
        val centralMeridian = (zone - 1) * 6 - 180 + 3

        val letters = "CDEFGHJKLMNPQRSTUVWX"
        val bandIdx = ((lat + 80.0) / 8.0).toInt().coerceIn(0, letters.length - 1)
        val band = letters[bandIdx]

        // WGS84 constants
        val a = 6378137.0 // semi-major axis
        val f = 1.0 / 298.257223563 // flattening
        val k0 = 0.9996 // scale factor
        val e2 = 2 * f - f * f // first eccentricity squared
        val ePrime2 = e2 / (1.0 - e2)

        val latRad = lat * PI / 180.0
        val lonRad = lon * PI / 180.0
        val centralLonRad = centralMeridian * PI / 180.0

        val n = a / sqrt(1.0 - e2 * sin(latRad).pow(2.0))
        val t = tan(latRad).pow(2.0)
        val c = ePrime2 * cos(latRad).pow(2.0)
        val deltaLon = lonRad - centralLonRad
        val aCos = cos(latRad) * deltaLon

        // Meridian distance M
        val m = a * ((1.0 - e2 / 4.0 - 3.0 * e2.pow(2.0) / 64.0 - 5.0 * e2.pow(3.0) / 256.0) * latRad -
            (3.0 * e2 / 8.0 + 3.0 * e2.pow(2.0) / 32.0 + 45.0 * e2.pow(3.0) / 1024.0) * sin(2.0 * latRad) +
            (15.0 * e2.pow(2.0) / 256.0 + 45.0 * e2.pow(3.0) / 1024.0) * sin(4.0 * latRad) -
            (35.0 * e2.pow(3.0) / 3072.0) * sin(6.0 * latRad))

        val easting = k0 * n * (aCos + (1.0 - t + c) * aCos.pow(3.0) / 6.0 +
            (5.0 - 18.0 * t + t.pow(2.0) + 72.0 * c - 58.0 * ePrime2) * aCos.pow(5.0) / 120.0) + 500000.0

        var northing = k0 * (m + n * tan(latRad) * (aCos.pow(2.0) / 2.0 +
            (5.0 - t + 9.0 * c + 4.0 * c.pow(2.0)) * aCos.pow(4.0) / 24.0 +
            (61.0 - 58.0 * t + t.pow(2.0) + 600.0 * c - 330.0 * ePrime2) * aCos.pow(6.0) / 720.0))

        if (lat < 0.0) northing += 10000000.0 // false northing for southern hemisphere

        return String.format(Locale.US, "%d%c %.0fm E %.0fm N", zone, band, easting, northing)
    }
}
