package com.geostamp.camera

import com.geostamp.camera.location.NmeaFixQuality
import com.geostamp.camera.location.NmeaLocation
import com.geostamp.camera.location.NmeaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserTest {

    @Test
    fun testCoordinateParsing() {
        // 28° 36.8340' N -> 28 + (36.8340 / 60) = 28.6139
        val lat = NmeaParser.parseCoord("2836.8340", "N")
        assertNotNull(lat)
        assertEquals(28.6139, lat!!, 0.0001)

        // 77° 12.5400' E -> 77 + (12.5400 / 60) = 77.2090
        val lon = NmeaParser.parseCoord("07712.5400", "E")
        assertNotNull(lon)
        assertEquals(77.2090, lon!!, 0.0001)

        // Southern hemisphere: 33° 51.6000' S -> -33.8600
        val latSouth = NmeaParser.parseCoord("3351.6000", "S")
        assertNotNull(latSouth)
        assertEquals(-33.8600, latSouth!!, 0.0001)

        // Western hemisphere: 122° 25.2000' W -> -122.4200
        val lonWest = NmeaParser.parseCoord("12225.2000", "W")
        assertNotNull(lonWest)
        assertEquals(-122.4200, lonWest!!, 0.0001)
    }

    @Test
    fun testRtkFixedGgaParsing() {
        // Standard NMEA GGA sentence with Fix Quality = 4 (RTK Fixed)
        val sentence = "\$GNGGA,123519,2836.8340,N,07712.5400,E,4,21,0.65,216.5,M,36.0,M,2.5,0100"
        val loc = NmeaParser.parseSentence(sentence)

        assertNotNull(loc)
        assertEquals(28.6139, loc!!.latitude!!, 0.0001)
        assertEquals(77.2090, loc.longitude!!, 0.0001)
        assertEquals(NmeaFixQuality.RTK_FIXED, loc.fixQuality)
        assertEquals(21, loc.satellitesInUse)
        assertEquals(0.65f, loc.hdop!!, 0.01f)
        assertEquals(216.5, loc.altitudeM!!, 0.1)
        assertTrue("RTK Fixed accuracy should be <= 0.03m, got ${loc.accuracyM}", loc.accuracyM!! <= 0.03f)
        assertTrue(loc.isRtk)
        assertTrue(loc.isAccurate)
    }

    @Test
    fun testRmcSpeedAndBearingParsing() {
        // NMEA RMC sentence: Status A, Speed 15.0 knots (27.78 km/h), Course 180.5°
        val sentence = "\$GNRMC,123519,A,2836.8340,N,07712.5400,E,15.0,180.5,211026,,,A"
        val loc = NmeaParser.parseSentence(sentence)

        assertNotNull(loc)
        assertEquals(28.6139, loc!!.latitude!!, 0.0001)
        assertEquals(77.2090, loc.longitude!!, 0.0001)
        assertEquals(27.78, loc.speedKmh!!, 0.1)
        assertEquals(180.5f, loc.bearingDeg!!, 0.1f)
    }

    @Test
    fun testInvalidSentenceOrChecksumMismatch() {
        // Corrupt sentence
        assertNull(NmeaParser.parseSentence("Not an NMEA sentence"))
        assertNull(NmeaParser.parseSentence("\$"))

        // Checksum mismatch (*FF is wrong)
        val badChecksum = "\$GNGGA,123519,2836.8340,N,07712.5400,E,1,08,1.2,216.5,M,,,,*FF"
        assertNull(NmeaParser.parseSentence(badChecksum))
    }
}
