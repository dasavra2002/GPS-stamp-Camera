package com.geostamp.camera

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReportGeneratorTest {

    @Test
    fun testKmlCoordinatesFormatting() {
        val lat = 28.6139
        val lon = 77.2090
        val alt = 216.5
        val kmlCoord = String.format(Locale.US, "%.6f,%.6f,%.1f", lon, lat, alt)
        // KML requires longitude first, then latitude, then altitude
        assertTrue(kmlCoord.startsWith("77.209000,28.613900,216.5"))
    }

    @Test
    fun testCsvEscaping() {
        fun escape(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        val rawAddress = "Connaught Place, \"Block A\", New Delhi"
        val escaped = escape(rawAddress)
        assertTrue(escaped.contains("\"\"Block A\"\""))
    }
}
