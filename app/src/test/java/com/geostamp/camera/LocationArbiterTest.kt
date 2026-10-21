package com.geostamp.camera

import com.geostamp.camera.location.LocationArbiter
import com.geostamp.camera.location.NmeaFixQuality
import com.geostamp.camera.location.NmeaLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationArbiterTest {

    @Test
    fun phoneIsUsedWithoutReceiver() {
        assertTrue(LocationArbiter().acceptPhoneFix(1_000))
    }

    @Test
    fun receiverOutranksPhoneWhileFresh() {
        val a = LocationArbiter(externalTimeoutMs = 5_000)
        a.onExternalFix(10_000)
        assertFalse(a.acceptPhoneFix(10_100))
        assertFalse(a.acceptPhoneFix(15_000))
        a.onExternalFix(15_000)
        assertFalse(a.acceptPhoneFix(19_000))
    }

    @Test
    fun phoneTakesOverWhenReceiverGoesQuiet() {
        val a = LocationArbiter(externalTimeoutMs = 5_000)
        a.onExternalFix(10_000)
        assertTrue(a.acceptPhoneFix(15_001))
    }

    @Test
    fun phoneTakesOverImmediatelyOnDisconnect() {
        val a = LocationArbiter(externalTimeoutMs = 5_000)
        a.onExternalFix(10_000)
        a.onExternalLost()
        assertTrue(a.acceptPhoneFix(10_001))
    }

    private fun fix(q: NmeaFixQuality) = NmeaLocation(latitude = 22.5, longitude = 88.3, fixQuality = q)

    @Test
    fun onlyRealSatelliteFixesAreUsable() {
        assertTrue(fix(NmeaFixQuality.RTK_FIXED).isUsableFix)
        assertTrue(fix(NmeaFixQuality.RTK_FLOAT).isUsableFix)
        assertTrue(fix(NmeaFixQuality.GPS).isUsableFix)
        assertFalse(fix(NmeaFixQuality.SIMULATION).isUsableFix)
        assertFalse(fix(NmeaFixQuality.MANUAL).isUsableFix)
        assertFalse(fix(NmeaFixQuality.ESTIMATED).isUsableFix)
        assertFalse(fix(NmeaFixQuality.INVALID).isUsableFix)
        assertFalse(NmeaLocation(fixQuality = NmeaFixQuality.RTK_FIXED).isUsableFix)
    }

    @Test
    fun sourceIdNamesFixType() {
        assertEquals("bluetooth-gnss:rtk-fixed", fix(NmeaFixQuality.RTK_FIXED).sourceId)
        assertEquals("bluetooth-gnss:dgps", fix(NmeaFixQuality.DGPS).sourceId)
    }
}
