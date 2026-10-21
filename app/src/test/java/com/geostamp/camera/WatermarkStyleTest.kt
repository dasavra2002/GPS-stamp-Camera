package com.geostamp.camera

import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.stamp.ShotParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkStyleTest {

    @Test
    fun testShotParamsFormatting_full() {
        val shot = ShotParams(
            focalMm = 26,
            aperture = 1.5f,
            shutterNs = 300_000_000L, // 0.3s
            iso = 12800,
            model = "POCO X7 Pro",
        )
        val formatted = shot.formatExifSummary()
        assertEquals("26mm  f/1.5  0.3s  ISO12800", formatted)
    }

    @Test
    fun testShotParamsFormatting_fastShutter() {
        val shot = ShotParams(
            focalMm = 50,
            aperture = 2.8f,
            shutterNs = 1_000_000_000L / 250, // 1/250s
            iso = 100,
        )
        val formatted = shot.formatExifSummary()
        assertEquals("50mm  f/2.8  1/250s  ISO100", formatted)
    }

    @Test
    fun testShotParamsFormatting_customSeparator() {
        val shot = ShotParams(
            focalMm = 24,
            aperture = 1.8f,
            shutterNs = 1_000_000_000L / 1000,
            iso = 400,
        )
        val formatted = shot.formatExifSummary(" · ")
        assertEquals("24mm · f/1.8 · 1/1000s · ISO400", formatted)
    }

    @Test
    fun testShotParamsFormatting_emptyFallback() {
        val shot = ShotParams()
        val formatted = shot.formatExifSummary()
        assertEquals("26mm  f/1.5  0.3s  ISO12800", formatted)
    }

    @Test
    fun testAllWatermarkStylesPresent() {
        val styles = WatermarkStyle.entries
        assertEquals(12, styles.size)
        assertTrue(styles.contains(WatermarkStyle.CLASSIC_GEO_BADGE))
        assertTrue(styles.contains(WatermarkStyle.STUDIO_WHITE_BAR))
        assertTrue(styles.contains(WatermarkStyle.ACCENT_BLOCK_BAR))
        assertTrue(styles.contains(WatermarkStyle.ACCENT_STRIPE_BAR))
        assertTrue(styles.contains(WatermarkStyle.AVATAR_WHITE_BAR))
        assertTrue(styles.contains(WatermarkStyle.EXHIBITION_WHITE_MAT))
        assertTrue(styles.contains(WatermarkStyle.FILM_STRIP_35MM))
        assertTrue(styles.contains(WatermarkStyle.SIGNATURE_YELLOW_MAT))
        assertTrue(styles.contains(WatermarkStyle.LUXURY_BLACK_MAT))
        assertTrue(styles.contains(WatermarkStyle.RETRO_VINTAGE_FILM))
        assertTrue(styles.contains(WatermarkStyle.EDITORIAL_TYPOGRAPHY))
        assertTrue(styles.contains(WatermarkStyle.POLAROID_SIDEBAR))
    }

    @Test
    fun testStampConfigWatermarkDefaultsAndOverrides() {
        val config = com.geostamp.camera.data.StampConfig(
            enabled = emptySet(),
            note = "",
            personName = "",
            phone = "",
            number = 1,
            mapType = com.geostamp.camera.data.MapType.STREET,
            font = com.geostamp.camera.data.FontStyle.SANS,
            color = com.geostamp.camera.data.StampColor.WHITE,
            background = com.geostamp.camera.data.StampBackground.DARK,
            size = com.geostamp.camera.data.StampSize.MEDIUM,
            position = com.geostamp.camera.data.StampPosition.BOTTOM,
            mapPosition = com.geostamp.camera.data.MapPosition.RIGHT,
            mapScalePercent = 100,
            dateFormat = com.geostamp.camera.data.DateFormat.DMY_24,
            logo = null,
            watermarkStyle = WatermarkStyle.FILM_STRIP_35MM,
            frameDataDensity = com.geostamp.camera.data.FrameDataDensity.FULL,
            deviceBrand = "Xiaomi 14",
            deviceBadge = "Ultra",
            customMotto = "Captured with Leica Summilux",
            showExifParams = true,
            showGpsOnFrame = false,
            showAvatarOnFrame = true,
        )
        assertEquals(WatermarkStyle.FILM_STRIP_35MM, config.watermarkStyle)
        assertEquals(com.geostamp.camera.data.FrameDataDensity.FULL, config.frameDataDensity)
        assertEquals("Xiaomi 14", config.deviceBrand)
        assertEquals("Ultra", config.deviceBadge)
        assertEquals("Captured with Leica Summilux", config.customMotto)
        assertTrue(config.showExifParams)
        assertFalse(config.showGpsOnFrame)
        assertTrue(config.showAvatarOnFrame)
    }

    @Test
    fun testFrameWatermarkRendererDataLines() {
        val enabledFields = setOf(
            com.geostamp.camera.data.StampField.TEMPERATURE,
            com.geostamp.camera.data.StampField.HUMIDITY,
            com.geostamp.camera.data.StampField.PROJECT,
            com.geostamp.camera.data.StampField.NUMBERING,
        )
        val config = com.geostamp.camera.data.StampConfig(
            enabled = enabledFields,
            note = "",
            personName = "",
            phone = "",
            siteName = "Central Hub",
            number = 42,
            mapType = com.geostamp.camera.data.MapType.STREET,
            font = com.geostamp.camera.data.FontStyle.SANS,
            color = com.geostamp.camera.data.StampColor.WHITE,
            background = com.geostamp.camera.data.StampBackground.DARK,
            size = com.geostamp.camera.data.StampSize.MEDIUM,
            position = com.geostamp.camera.data.StampPosition.BOTTOM,
            mapPosition = com.geostamp.camera.data.MapPosition.RIGHT,
            mapScalePercent = 100,
            dateFormat = com.geostamp.camera.data.DateFormat.DMY_24,
            logo = null,
            frameDataDensity = com.geostamp.camera.data.FrameDataDensity.FULL,
        )
        val data = com.geostamp.camera.data.StampData(
            temperatureC = 27.5,
            humidity = 68,
        )

        val weather = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildWeatherAndSensorsLine(data, config)
        assertTrue(weather.contains("27.5°C"))
        assertTrue(weather.contains("68%"))

        val work = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildWorkAndProjectLine(config, "Back")
        assertTrue(work.contains("#0042"))
        assertTrue(work.contains("Site: Central Hub"))
    }

    @Test
    fun testFrameWatermarkRendererWorkLineExcludesDefaultNote() {
        val configWithDefaultNote = com.geostamp.camera.data.StampConfig(
            enabled = setOf(com.geostamp.camera.data.StampField.NOTE, com.geostamp.camera.data.StampField.NUMBERING),
            note = "Captured by GeoStamp Camera",
            personName = "",
            phone = "",
            number = 5,
            mapType = com.geostamp.camera.data.MapType.STREET,
            font = com.geostamp.camera.data.FontStyle.SANS,
            color = com.geostamp.camera.data.StampColor.WHITE,
            background = com.geostamp.camera.data.StampBackground.DARK,
            size = com.geostamp.camera.data.StampSize.MEDIUM,
            position = com.geostamp.camera.data.StampPosition.BOTTOM,
            mapPosition = com.geostamp.camera.data.MapPosition.RIGHT,
            mapScalePercent = 100,
            dateFormat = com.geostamp.camera.data.DateFormat.DMY_24,
            logo = null,
        )
        val line = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildWorkAndProjectLine(configWithDefaultNote, null)
        assertEquals("#0005", line)
        assertFalse(line.contains("Captured by GeoStamp Camera"))

        val configWithCustomNote = configWithDefaultNote.copy(note = "Bridge inspection sector 4")
        val customLine = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildWorkAndProjectLine(configWithCustomNote, null)
        assertTrue(customLine.contains("Bridge inspection sector 4"))
    }

    @Test
    fun testFrameWatermarkRendererBuildAddressLine() {
        val config = com.geostamp.camera.data.StampConfig(
            enabled = setOf(
                com.geostamp.camera.data.StampField.COUNTRY_FLAG,
                com.geostamp.camera.data.StampField.ADDRESS,
            ),
            note = "",
            personName = "",
            phone = "",
            number = 1,
            mapType = com.geostamp.camera.data.MapType.STREET,
            font = com.geostamp.camera.data.FontStyle.SANS,
            color = com.geostamp.camera.data.StampColor.WHITE,
            background = com.geostamp.camera.data.StampBackground.DARK,
            size = com.geostamp.camera.data.StampSize.MEDIUM,
            position = com.geostamp.camera.data.StampPosition.BOTTOM,
            mapPosition = com.geostamp.camera.data.MapPosition.RIGHT,
            mapScalePercent = 100,
            dateFormat = com.geostamp.camera.data.DateFormat.DMY_24,
            logo = null,
        )
        val data = com.geostamp.camera.data.StampData(
            countryCode = "IN",
            address = "Roorkee, Uttarakhand 247667, India",
        )
        val addrLine = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildAddressLine(data, config)
        assertTrue(addrLine.contains("🇮🇳"))
        assertTrue(addrLine.contains("Roorkee, Uttarakhand 247667, India"))
    }

    @Test
    fun testFrameWatermarkRendererBuildGpsAndLocationLine() {
        val config = com.geostamp.camera.data.StampConfig(
            enabled = setOf(
                com.geostamp.camera.data.StampField.LAT_LONG,
                com.geostamp.camera.data.StampField.ALTITUDE,
                com.geostamp.camera.data.StampField.PLUS_CODE,
            ),
            note = "",
            personName = "",
            phone = "",
            number = 1,
            mapType = com.geostamp.camera.data.MapType.STREET,
            font = com.geostamp.camera.data.FontStyle.SANS,
            color = com.geostamp.camera.data.StampColor.WHITE,
            background = com.geostamp.camera.data.StampBackground.DARK,
            size = com.geostamp.camera.data.StampSize.MEDIUM,
            position = com.geostamp.camera.data.StampPosition.BOTTOM,
            mapPosition = com.geostamp.camera.data.MapPosition.RIGHT,
            mapScalePercent = 100,
            dateFormat = com.geostamp.camera.data.DateFormat.DMY_24,
            coordinateFormat = com.geostamp.camera.data.CoordinateFormat.DECIMAL,
            logo = null,
        )
        val data = com.geostamp.camera.data.StampData(
            latitude = 29.8630,
            longitude = 77.8924,
            altitude = 268.0,
            plusCode = "7JWV+5C Roorkee",
        )
        val gpsLine = com.geostamp.camera.stamp.FrameWatermarkRenderer.buildGpsAndLocationLine(data, config)
        assertTrue(gpsLine.contains("29.863000° N"))
        assertTrue(gpsLine.contains("268m"))
        assertTrue(gpsLine.contains("7JWV+5C Roorkee"))
    }
}
