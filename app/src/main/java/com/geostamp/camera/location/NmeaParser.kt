package com.geostamp.camera.location

import java.util.Locale
import kotlin.math.floor

enum class NmeaFixQuality(val code: Int, val label: String) {
    INVALID(0, "No Fix"),
    GPS(1, "GPS Autonomous (3-5m)"),
    DGPS(2, "DGPS / SBAS (1-2m)"),
    PPS(3, "PPS Precise"),
    RTK_FIXED(4, "RTK Fixed (±0.02m)"),
    RTK_FLOAT(5, "RTK Float (±0.2m)"),
    ESTIMATED(6, "Dead Reckoning"),
    MANUAL(7, "Manual Input"),
    SIMULATION(8, "Simulation");

    companion object {
        fun fromCode(code: Int): NmeaFixQuality {
            return entries.firstOrNull { it.code == code } ?: INVALID
        }
    }
}

data class NmeaLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val satellitesInUse: Int = 0,
    val hdop: Float? = null,
    val speedKmh: Double? = null,
    val bearingDeg: Float? = null,
    val fixQuality: NmeaFixQuality = NmeaFixQuality.INVALID,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val isRtk: Boolean
        get() = fixQuality == NmeaFixQuality.RTK_FIXED || fixQuality == NmeaFixQuality.RTK_FLOAT

    val isAccurate: Boolean
        get() = fixQuality != NmeaFixQuality.INVALID && latitude != null && longitude != null

    /**
     * A real satellite fix that may go on a stamp. Dead reckoning, manual input and
     * simulation modes are excluded: a receiver in simulation mode would otherwise fake the location.
     */
    val isUsableFix: Boolean
        get() = isAccurate && fixQuality in USABLE

    /** Short id recorded in the photo's proof, e.g. "bluetooth-gnss:rtk-fixed". */
    val sourceId: String
        get() = "bluetooth-gnss:" + fixQuality.name.lowercase(Locale.US).replace('_', '-')

    private companion object {
        val USABLE = setOf(
            NmeaFixQuality.GPS, NmeaFixQuality.DGPS, NmeaFixQuality.PPS,
            NmeaFixQuality.RTK_FIXED, NmeaFixQuality.RTK_FLOAT,
        )
    }
}

object NmeaParser {

    /**
     * Parses standard NMEA sentences ($GPGGA, $GNGGA, $GPRMC, $GNRMC, etc.)
     * Returns an updated [NmeaLocation], or null if unparseable.
     */
    fun parseSentence(sentence: String, current: NmeaLocation = NmeaLocation()): NmeaLocation? {
        val trimmed = sentence.trim()
        if (!trimmed.startsWith("$")) return null

        // Checksum verification if present (*XX)
        val starIdx = trimmed.indexOf('*')
        val payload = if (starIdx != -1) trimmed.substring(1, starIdx) else trimmed.substring(1)
        if (starIdx != -1 && trimmed.length >= starIdx + 3) {
            val expectedChecksum = trimmed.substring(starIdx + 1, starIdx + 3).toIntOrNull(16)
            if (expectedChecksum != null) {
                var calc = 0
                for (ch in payload) {
                    calc = calc xor ch.code
                }
                if (calc != expectedChecksum) {
                    return null // Checksum mismatch
                }
            }
        }

        val tokens = payload.split(',')
        if (tokens.isEmpty()) return null
        val type = tokens[0].uppercase(Locale.US)

        return when {
            type.endsWith("GGA") -> parseGga(tokens, current)
            type.endsWith("RMC") -> parseRmc(tokens, current)
            else -> null
        }
    }

    private fun parseGga(tokens: List<String>, current: NmeaLocation): NmeaLocation? {
        // $--GGA,time,lat,NS,lon,EW,quality,numSV,HDOP,alt,M,sep,M,diffAge,diffStation*cs
        if (tokens.size < 10) return null

        val lat = parseCoord(tokens.getOrNull(2), tokens.getOrNull(3))
        val lon = parseCoord(tokens.getOrNull(4), tokens.getOrNull(5))
        val qualityCode = tokens.getOrNull(6)?.toIntOrNull() ?: 0
        val quality = NmeaFixQuality.fromCode(qualityCode)
        val sats = tokens.getOrNull(7)?.toIntOrNull() ?: current.satellitesInUse
        val hdop = tokens.getOrNull(8)?.toFloatOrNull() ?: current.hdop
        val alt = tokens.getOrNull(9)?.toDoubleOrNull() ?: current.altitudeM

        val estimatedAccuracy: Float? = when (quality) {
            NmeaFixQuality.RTK_FIXED -> (hdop?.times(0.02f) ?: 0.02f).coerceAtLeast(0.01f)
            NmeaFixQuality.RTK_FLOAT -> (hdop?.times(0.25f) ?: 0.20f).coerceAtLeast(0.10f)
            NmeaFixQuality.DGPS -> (hdop?.times(1.5f) ?: 1.5f).coerceAtLeast(0.8f)
            NmeaFixQuality.GPS -> (hdop?.times(3.5f) ?: 3.5f).coerceAtLeast(2.0f)
            else -> null
        }

        return current.copy(
            latitude = lat ?: current.latitude,
            longitude = lon ?: current.longitude,
            altitudeM = alt,
            accuracyM = estimatedAccuracy ?: current.accuracyM,
            satellitesInUse = sats,
            hdop = hdop,
            fixQuality = quality,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun parseRmc(tokens: List<String>, current: NmeaLocation): NmeaLocation? {
        // $--RMC,time,status,lat,NS,lon,EW,spdKnots,cogDeg,date,...
        if (tokens.size < 9) return null

        val status = tokens.getOrNull(2)
        if (status != "A") return null // V = Warning / Void

        val lat = parseCoord(tokens.getOrNull(3), tokens.getOrNull(4))
        val lon = parseCoord(tokens.getOrNull(5), tokens.getOrNull(6))
        val speedKnots = tokens.getOrNull(7)?.toDoubleOrNull()
        val speedKmh = speedKnots?.times(1.852)
        val bearing = tokens.getOrNull(8)?.toFloatOrNull()

        return current.copy(
            latitude = lat ?: current.latitude,
            longitude = lon ?: current.longitude,
            speedKmh = speedKmh ?: current.speedKmh,
            bearingDeg = bearing ?: current.bearingDeg,
            timestampMs = System.currentTimeMillis()
        )
    }

    /** Converts NMEA coordinate format (ddmm.mmmm or dddmm.mmmm) into decimal degrees. */
    fun parseCoord(coordStr: String?, direction: String?): Double? {
        if (coordStr.isNullOrBlank() || direction.isNullOrBlank()) return null
        val raw = coordStr.toDoubleOrNull() ?: return null

        val deg = floor(raw / 100.0)
        val minutes = raw - (deg * 100.0)
        var decimal = deg + (minutes / 60.0)

        val dir = direction.trim().uppercase(Locale.US)
        if (dir == "S" || dir == "W") {
            decimal = -decimal
        }
        return decimal
    }
}
