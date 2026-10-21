package com.geostamp.camera

import com.geostamp.camera.stamp.StampQrCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class StampQrCodeTest {

    @Test
    fun testPayloadStructure() {
        val time = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC)
        val payload = StampQrCode.buildPayload(28.6139, 77.2090, 216.0, time, "7JWX0000+00", 42)
        assertTrue(payload.contains("LAT: 28.613900"))
        assertTrue(payload.contains("LON: 77.209000"))
        assertTrue(payload.contains("ALT: 216m"))
        assertTrue(payload.contains("PHOTO #42"))
        assertTrue(payload.contains("https://maps.google.com/?q=28.613900,77.209000"))
    }

    @Test
    fun testPayloadDoesNotClaimVerification() {
        val time = ZonedDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC)
        val payload = StampQrCode.buildPayload(28.6139, 77.2090, null, time, null, 1)
        assertFalse(payload.contains("VERIFIED"))
    }
}
