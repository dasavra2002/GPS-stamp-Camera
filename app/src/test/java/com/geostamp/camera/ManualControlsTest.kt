package com.geostamp.camera

import com.geostamp.camera.camera.Manual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualControlsTest {

    @Test
    fun testShutterSpeedConversions() {
        // 1/1000s -> 1,000,000 ns
        assertEquals(1_000_000L, Manual.speedToNs(1000))
        // 1/60s -> 16,666,666 ns
        assertEquals(16_666_666L, Manual.speedToNs(60))
        // 1s -> 1,000,000,000 ns
        assertEquals(1_000_000_000L, Manual.speedToNs(1))
        // Long exposures: -2s -> 2,000,000,000 ns
        assertEquals(2_000_000_000L, Manual.speedToNs(-2))
    }

    @Test
    fun testShutterAngleConversions() {
        // 180° shutter at 24 fps = 1/48s = ~20.83ms = 20,833,333 ns
        val ns24 = Manual.angleToNs(180f, 24)
        val expectedNs24 = (180f / 360f / 24f * 1e9f).toLong()
        assertEquals(expectedNs24, ns24)

        // Convert back from ns to angle
        val angle = Manual.nsToAngle(ns24, 24)
        assertEquals(180f, angle, 0.05f)
    }

    @Test
    fun testLabels() {
        // Speed label
        assertEquals("1/1000", Manual.shutterLabel(1_000_000L))
        assertEquals("1/60", Manual.shutterLabel(16_666_666L))
        assertEquals("1.0s", Manual.shutterLabel(1_000_000_000L))
        assertEquals("2.0s", Manual.shutterLabel(2_000_000_000L))

        // Angle label
        assertEquals("180°", Manual.angleLabel(180f))
        assertEquals("172.8°", Manual.angleLabel(172.8f))

        // Focus label
        assertEquals("AF", Manual.focusLabel(null))
        assertEquals("∞", Manual.focusLabel(0.005f))
        assertEquals("1.00m", Manual.focusLabel(1.0f))
        assertEquals("0.50m", Manual.focusLabel(2.0f))
    }

    @Test
    fun testKelvinGains() {
        // Kelvin gains runs the blackbody algorithm and returns an RggbChannelVector
        val warmGains = Manual.kelvinGains(3200, 0)
        assertNotNull(warmGains)

        val coolGains = Manual.kelvinGains(7500, 0)
        assertNotNull(coolGains)

        // Extreme bounds check (coerced within 1500..15000)
        val lowGains = Manual.kelvinGains(1000, 0)
        assertNotNull(lowGains)
        val highGains = Manual.kelvinGains(20000, 0)
        assertNotNull(highGains)
    }
}
