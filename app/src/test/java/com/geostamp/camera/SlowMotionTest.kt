package com.geostamp.camera

import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.data.SlowMoSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowMotionTest {

    @Test
    fun testSlowMoSpeeds() {
        assertEquals(60, SlowMoSpeed.X2.targetFps)
        assertEquals(120, SlowMoSpeed.X4.targetFps)
        assertEquals(240, SlowMoSpeed.X8.targetFps)

        assertEquals("2× (60fps)", SlowMoSpeed.X2.label)
        assertEquals("4× (120fps)", SlowMoSpeed.X4.label)
        assertEquals("8× (240fps)", SlowMoSpeed.X8.label)
    }

    @Test
    fun testCaptureModeIsVideo() {
        assertTrue(CaptureMode.VIDEO.isVideo)
        assertTrue(CaptureMode.SLOW_MO.isVideo)
        assertFalse(CaptureMode.PHOTO.isVideo)
        assertFalse(CaptureMode.TIMELAPSE.isVideo)
    }

    @Test
    fun testCaptureModeEnumParsing() {
        val mode = CaptureMode.valueOf("SLOW_MO")
        assertEquals(CaptureMode.SLOW_MO, mode)
        assertTrue(mode.isVideo)
    }
}
