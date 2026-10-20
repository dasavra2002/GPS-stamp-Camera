package com.geostamp.camera

import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimelapseAndTemplateTest {

    @Test
    fun testStampPositionEntries() {
        val names = StampPosition.entries.map { it.name }
        assertTrue("StampPosition must include BOTTOM", names.contains("BOTTOM"))
        assertTrue("StampPosition must include TOP", names.contains("TOP"))
        assertTrue("StampPosition must include CENTER", names.contains("CENTER"))
    }

    @Test
    fun testTimelapseIntervalCycle() {
        fun nextInterval(current: Int): Int = when (current) {
            1 -> 2
            2 -> 5
            5 -> 10
            10 -> 30
            else -> 1
        }

        assertEquals(2, nextInterval(1))
        assertEquals(5, nextInterval(2))
        assertEquals(10, nextInterval(5))
        assertEquals(30, nextInterval(10))
        assertEquals(1, nextInterval(30))
    }

    @Test
    fun testTimelapseFolderNaming() {
        val now = ZonedDateTime.now()
        val sessionTag = "Timelapse_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(now)
        assertTrue(sessionTag.startsWith("Timelapse_"))
        assertEquals(25, sessionTag.length)

        val siteName = "Project Site Alpha"
        val subFolder = "$siteName/$sessionTag"
        val safe = subFolder.trim().replace(Regex("[\\\\:*?\"<>|]"), "_")
        assertEquals("Project Site Alpha/$sessionTag", safe)
    }

    @Test
    fun testBuiltInTemplatesIntegrity() {
        assertEquals("Standard (All Fields)", StampTemplate.STANDARD.label)
        assertEquals("Field Inspection & Proof of Work", StampTemplate.INSPECTION.label)
        assertEquals("Selfie Attendance Punch", StampTemplate.ATTENDANCE.label)
        assertEquals("Minimal (GPS & Time Only)", StampTemplate.MINIMAL.label)
    }
}
