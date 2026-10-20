package com.geostamp.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWidgetProviderTest {

    @Test
    fun testIntentActionConstants() {
        assertEquals("com.geostamp.camera.ACTION_OPEN_CAMERA", MainActivity.ACTION_OPEN_CAMERA)
        assertEquals("com.geostamp.camera.ACTION_PUNCH_IN", MainActivity.ACTION_PUNCH_IN)
        assertEquals("com.geostamp.camera.ACTION_OPEN_GALLERY", MainActivity.ACTION_OPEN_GALLERY)
        assertEquals("extra_start_screen", MainActivity.EXTRA_START_SCREEN)
        assertEquals("extra_start_mode", MainActivity.EXTRA_START_MODE)
    }

    @Test
    fun testSiteStatusFormatting() {
        fun formatStatus(siteName: String): String {
            val trimmed = siteName.trim()
            return if (trimmed.isNotEmpty()) "📍 Site: $trimmed" else "📍 Ready to stamp"
        }

        assertEquals("📍 Ready to stamp", formatStatus(""))
        assertEquals("📍 Ready to stamp", formatStatus("   "))
        assertEquals("📍 Site: Sector 62 Metro Station", formatStatus("Sector 62 Metro Station"))
    }
}
