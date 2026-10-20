package com.geostamp.camera

import com.geostamp.camera.stamp.PlusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCodeTest {

    @Test
    fun testValidFormat() {
        val code = PlusCode.encode(28.6139, 77.2090) // New Delhi
        assertNotNull(code)
        assertEquals(11, code.length)
        assertEquals('+', code[8])
        val alphabet = "23456789CFGHJMPQRVWX"
        for (i in 0 until 11) {
            if (i == 8) continue
            assertTrue("Char at $i must be in alphabet, was ${code[i]}", code[i] in alphabet)
        }
    }

    @Test
    fun testKnownReferenceCode() {
        // IMPROVEMENTS.md noted reference check: 7JXWW5V4+28
        // Let's verify that encoding coordinates in that area generates the same prefix
        val code = PlusCode.encode(28.6139, 77.2090)
        assertTrue(code.startsWith("7J"))
    }

    @Test
    fun testBoundaryCoordinates() {
        // Poles and International Date Line
        val northPole = PlusCode.encode(90.0, 0.0)
        assertEquals(11, northPole.length)
        assertEquals('+', northPole[8])

        val southPole = PlusCode.encode(-90.0, 0.0)
        assertEquals(11, southPole.length)
        assertEquals('+', southPole[8])

        val datelinePositive = PlusCode.encode(0.0, 180.0)
        val datelineNegative = PlusCode.encode(0.0, -180.0)
        assertEquals(datelinePositive, datelineNegative)

        val nullIsland = PlusCode.encode(0.0, 0.0)
        assertEquals("6FG22222+22", nullIsland)
    }

    @Test
    fun testWrapAround() {
        val standard = PlusCode.encode(12.9716, 77.5946) // Bengaluru
        val wrapped = PlusCode.encode(12.9716, 77.5946 + 360.0)
        assertEquals(standard, wrapped)
    }
}
