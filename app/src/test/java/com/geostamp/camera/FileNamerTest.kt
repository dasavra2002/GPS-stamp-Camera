package com.geostamp.camera

import com.geostamp.camera.data.FileNameField
import com.geostamp.camera.data.FileNameSeparator
import com.geostamp.camera.data.FileNameValues
import com.geostamp.camera.data.FileNamer
import com.geostamp.camera.data.FileNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FileNamerTest {
    private val time = ZonedDateTime.of(2026, 11, 27, 9, 30, 12, 481_000_000, ZoneId.of("Asia/Kolkata"))
    private val values = FileNameValues(
        time = time,
        latitude = 28.6139,
        longitude = 77.2090,
        altitude = 216.4,
        place = "New Delhi, Delhi, India",
        shortAddress = "Rajpath / Kartavya Path",
        countryCode = "in",
        site = "Site: A*1",
        number = 42,
    )

    private fun naming(vararg f: FileNameField, sep: FileNameSeparator = FileNameSeparator.UNDERSCORE) =
        FileNaming(f.toList(), "GeoStamp", sep)

    @Test
    fun defaultMatchesOldNames() {
        assertEquals("GeoStamp_20261127_093012_481.jpg", FileNamer.fileName(FileNaming.DEFAULT, values))
    }

    @Test
    fun coordinatesUseHemisphereLetters() {
        assertEquals("28.61390N_77.20900E", FileNamer.coordinates(28.6139, 77.2090))
        assertEquals("33.86880S-151.20930E", FileNamer.coordinates(-33.8688, 151.2093, '-'))
        assertEquals("40.71280N_74.00600W", FileNamer.coordinates(40.7128, -74.0060))
    }

    @Test
    fun plusCodeIsComputedWhenMissingAndKeepsPlus() {
        val name = FileNamer.baseName(naming(FileNameField.PLUS_CODE), values)
        assertTrue(name.startsWith("7J"))
        assertEquals('+', name[8])
    }

    @Test
    fun fieldsFollowChosenOrder() {
        val name = FileNamer.baseName(
            naming(FileNameField.SITE, FileNameField.NUMBER, FileNameField.LAT_LONG, FileNameField.ALTITUDE, FileNameField.COUNTRY),
            values,
        )
        assertEquals("Site-A-1_0042_28.61390N_77.20900E_216m_IN", name)
    }

    @Test
    fun freeTextIsMadeSafe() {
        val name = FileNamer.baseName(naming(FileNameField.PLACE, FileNameField.SHORT_ADDRESS), values)
        assertEquals("New-Delhi-Delhi-India_Rajpath-Kartavya-Path", name)
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', ' ', ',').forEach { assertFalse(name.contains(it)) }
    }

    @Test
    fun nonLatinLettersAreKept() {
        assertEquals("नई-दिल्ली", FileNamer.clean("नई दिल्ली"))
    }

    @Test
    fun missingValuesAreSkipped() {
        val v = FileNameValues(time = time)
        assertEquals("GeoStamp_20261127_093012_481", FileNamer.baseName(naming(FileNameField.PREFIX, FileNameField.LAT_LONG, FileNameField.PLUS_CODE, FileNameField.DATE_TIME), v))
    }

    @Test
    fun emptyTemplateFallsBackToTime() {
        assertEquals("20261127_093012_481", FileNamer.baseName(naming(), values))
    }

    @Test
    fun prefixOverrideAndDashSeparator() {
        val n = naming(FileNameField.PREFIX, FileNameField.DATE_TIME, sep = FileNameSeparator.DASH)
        assertEquals("Frame-20261127-093012-481", FileNamer.baseName(n, values, prefixOverride = "Frame"))
    }

    @Test
    fun longNamesStayUnderByteLimit() {
        val v = values.copy(place = "स्थान ".repeat(100), shortAddress = "x".repeat(300), site = "ஆ".repeat(200))
        val name = FileNamer.fileName(naming(FileNameField.PLACE, FileNameField.SHORT_ADDRESS, FileNameField.SITE, FileNameField.DATE_TIME), v)
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun fieldOrderRoundTrips() {
        val list = listOf(FileNameField.PLUS_CODE, FileNameField.PREFIX, FileNameField.LAT_LONG)
        assertEquals(list, FileNaming.decodeFields(FileNaming.encodeFields(list)))
        assertEquals(listOf(FileNameField.DATE), FileNaming.decodeFields("bogus,date,date"))
    }
}
