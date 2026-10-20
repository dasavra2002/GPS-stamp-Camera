package com.geostamp.camera

import com.geostamp.camera.data.CoordinateConverter
import com.geostamp.camera.data.CoordinateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateConverterTest {

    @Test
    fun testDecimalDegrees() {
        val str = CoordinateConverter.format(28.613900, 77.209000, CoordinateFormat.DECIMAL)
        assertEquals("Lat 28.613900° N Long 77.209000° E", str)

        val southern = CoordinateConverter.format(-33.868800, -151.209300, CoordinateFormat.DECIMAL)
        assertEquals("Lat 33.868800° S Long 151.209300° W", southern)
    }

    @Test
    fun testDegreesMinutesSeconds() {
        val str = CoordinateConverter.format(28.6139, 77.2090, CoordinateFormat.DMS)
        // 28.6139° = 28° 36' 50.0" N
        assertTrue(str.startsWith("28°36'50.0\"N"))
        assertTrue(str.contains("77°12'32.4\"E"))
    }

    @Test
    fun testUtmConversion() {
        // New Delhi: 28.6139 N, 77.2090 E is in UTM Zone 43R
        val utm = CoordinateConverter.format(28.6139, 77.2090, CoordinateFormat.UTM)
        assertTrue("UTM for New Delhi must be in Zone 43R, was: $utm", utm.startsWith("43R"))
        assertTrue(utm.contains("m E"))
        assertTrue(utm.contains("m N"))

        // Paris: 48.8566 N, 2.3522 E is in UTM Zone 31U
        val utmParis = CoordinateConverter.format(48.8566, 2.3522, CoordinateFormat.UTM)
        assertTrue("UTM for Paris must be in Zone 31U, was: $utmParis", utmParis.startsWith("31U"))
    }
}
