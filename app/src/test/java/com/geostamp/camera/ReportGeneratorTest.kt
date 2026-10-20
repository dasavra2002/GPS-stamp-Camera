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

    @Test
    fun testPdfPageCalculationWithSignoff() {
        fun calcTotalPages(itemCount: Int, itemsPerPage: Int = 2): Int {
            val itemsOnLastPage = itemCount % itemsPerPage
            return if (itemCount == 0) 0 else if (itemsOnLastPage == 1) {
                (itemCount + 1) / itemsPerPage
            } else {
                (itemCount / itemsPerPage) + 1
            }
        }

        // 1 photo -> fits on page 1 together with sign-off card
        org.junit.Assert.assertEquals(1, calcTotalPages(1))
        // 2 photos -> page 1 is full with 2 photos, page 2 has sign-off certificate
        org.junit.Assert.assertEquals(2, calcTotalPages(2))
        // 3 photos -> 2 photos on page 1, 1 photo + sign-off on page 2
        org.junit.Assert.assertEquals(2, calcTotalPages(3))
        // 4 photos -> 2 on page 1, 2 on page 2, page 3 has sign-off certificate
        org.junit.Assert.assertEquals(3, calcTotalPages(4))
    }
}
