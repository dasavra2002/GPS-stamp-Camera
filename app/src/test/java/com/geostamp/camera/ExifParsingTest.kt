package com.geostamp.camera

import com.geostamp.camera.stamp.GalleryStamper
import com.geostamp.camera.stamp.WatermarkRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class ExifParsingTest {

    @Test
    fun testStandardExifDateWithOffset() {
        val dt = GalleryStamper.parseExifDateTime("2026:10:17 23:14:05", "+05:30")
        assertNotNull(dt)
        assertEquals(2026, dt!!.year)
        assertEquals(10, dt.monthValue)
        assertEquals(17, dt.dayOfMonth)
        assertEquals(23, dt.hour)
        assertEquals(14, dt.minute)
        assertEquals(5, dt.second)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), dt.offset)
    }

    @Test
    fun testAlternativeSeparators() {
        // Hyphen format
        val dtHyphen = GalleryStamper.parseExifDateTime("2026-10-17 23:14:05", "+00:00")
        assertNotNull(dtHyphen)
        assertEquals(2026, dtHyphen!!.year)
        assertEquals(ZoneOffset.UTC, dtHyphen.offset)

        // Slash format
        val dtSlash = GalleryStamper.parseExifDateTime("2026/10/17 23:14:05", "-04:00")
        assertNotNull(dtSlash)
        assertEquals(17, dtSlash!!.dayOfMonth)
        assertEquals(ZoneOffset.ofHours(-4), dtSlash.offset)

        // ISO format with 'T'
        val dtIso = GalleryStamper.parseExifDateTime("2026-10-17T23:14:05", null)
        assertNotNull(dtIso)
        assertEquals(2026, dtIso!!.year)
    }

    @Test
    fun testInvalidOrEmptyExifDate() {
        assertNull(GalleryStamper.parseExifDateTime(null, null))
        assertNull(GalleryStamper.parseExifDateTime("", null))
        assertNull(GalleryStamper.parseExifDateTime("   ", null))
        assertNull(GalleryStamper.parseExifDateTime("invalid date format", "+05:30"))
    }

    @Test
    fun testDirectionCompass() {
        assertEquals("N", WatermarkRenderer.direction(0f))
        assertEquals("NE", WatermarkRenderer.direction(45f))
        assertEquals("E", WatermarkRenderer.direction(90f))
        assertEquals("SE", WatermarkRenderer.direction(135f))
        assertEquals("S", WatermarkRenderer.direction(180f))
        assertEquals("SW", WatermarkRenderer.direction(225f))
        assertEquals("W", WatermarkRenderer.direction(270f))
        assertEquals("NW", WatermarkRenderer.direction(315f))
        assertEquals("N", WatermarkRenderer.direction(359f))
        assertEquals("N", WatermarkRenderer.direction(720f))
    }

    @Test
    fun testCountryFlagEmoji() {
        // India (IN) -> 🇮🇳
        val inFlag = WatermarkRenderer.flagEmoji("IN")
        val expectedIn = String(Character.toChars(0x1F1EE)) + String(Character.toChars(0x1F1F3))
        assertEquals(expectedIn, inFlag)

        // United States (US) -> 🇺🇸
        val usFlag = WatermarkRenderer.flagEmoji("US")
        val expectedUs = String(Character.toChars(0x1F1FA)) + String(Character.toChars(0x1F1F8))
        assertEquals(expectedUs, usFlag)

        // Case insensitivity
        assertEquals(inFlag, WatermarkRenderer.flagEmoji("in"))

        // Invalid codes return empty
        assertEquals("", WatermarkRenderer.flagEmoji(null))
        assertEquals("", WatermarkRenderer.flagEmoji(""))
        assertEquals("", WatermarkRenderer.flagEmoji("IND"))
        assertEquals("", WatermarkRenderer.flagEmoji("12"))
    }
}
