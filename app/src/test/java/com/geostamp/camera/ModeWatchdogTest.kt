package com.geostamp.camera

import com.geostamp.camera.camera.ModeWatchdog
import com.geostamp.camera.camera.ModeWatchdog.Verdict
import com.geostamp.camera.camera.SpecialMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeWatchdogTest {
    @Test
    fun fatalErrorFailsAtOnce() {
        assertEquals(Verdict.FAILED, ModeWatchdog.verdict(0, streaming = false, cameraOpen = false, fatalError = true))
    }

    @Test
    fun staleStreamingRightAfterBindIsIgnored() {
        assertEquals(Verdict.WAIT, ModeWatchdog.verdict(100, streaming = true, cameraOpen = true, fatalError = false))
    }

    @Test
    fun streamingAfterSettleIsOk() {
        assertEquals(Verdict.OK, ModeWatchdog.verdict(1_000, streaming = true, cameraOpen = true, fatalError = false))
    }

    @Test
    fun streamingNeedsAnOpenCamera() {
        assertEquals(Verdict.WAIT, ModeWatchdog.verdict(1_000, streaming = true, cameraOpen = false, fatalError = false))
    }

    @Test
    fun blackViewfinderTimesOut() {
        assertEquals(Verdict.WAIT, ModeWatchdog.verdict(ModeWatchdog.START_TIMEOUT_MS - 1, false, true, false))
        assertEquals(Verdict.FAILED, ModeWatchdog.verdict(ModeWatchdog.START_TIMEOUT_MS, false, true, false))
    }

    @Test
    fun labels() {
        assertEquals("RAW", SpecialMode("0", raw = true, hdr = false).label)
        assertEquals("HDR", SpecialMode("0", raw = false, hdr = true).label)
        assertTrue(ModeWatchdog.fallbackMessage(SpecialMode("2", true, false), "Ultra-wide").startsWith("RAW couldn't start"))
    }
}
