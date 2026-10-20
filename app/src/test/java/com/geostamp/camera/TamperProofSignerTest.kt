package com.geostamp.camera

import com.geostamp.camera.stamp.TamperProofSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TamperProofSignerTest {

    @Test
    fun testSignatureConsistency() {
        val time = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC)
        val sig1 = TamperProofSigner.computeSignature(28.6139, 77.2090, 216.0, time, 1)
        val sig2 = TamperProofSigner.computeSignature(28.6139, 77.2090, 216.0, time, 1)
        assertEquals(sig1, sig2)
        assertEquals(12, sig1.length)
    }

    @Test
    fun testTamperDetection() {
        val time = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC)
        val originalSig = TamperProofSigner.computeSignature(28.6139, 77.2090, 216.0, time, 1)
        // Modified coordinate
        val tamperedSig = TamperProofSigner.computeSignature(28.6140, 77.2090, 216.0, time, 1)
        assertTrue("Altering coordinate must change signature", originalSig != tamperedSig)
    }

    @Test
    fun testPayloadStructure() {
        val time = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC)
        val payload = TamperProofSigner.buildPayload(28.6139, 77.2090, 216.0, time, "7JWX0000+00", 42)
        assertTrue(payload.contains("LAT: 28.613900"))
        assertTrue(payload.contains("LON: 77.209000"))
        assertTrue(payload.contains("ALT: 216m"))
        assertTrue(payload.contains("PHOTO #42"))
        assertTrue(payload.contains("VERIFIED SIG:"))
        assertTrue(payload.contains("https://maps.google.com/?q=28.613900,77.209000"))
    }
}
