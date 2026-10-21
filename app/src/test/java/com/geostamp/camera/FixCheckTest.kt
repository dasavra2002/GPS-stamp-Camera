package com.geostamp.camera

import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.location.FixIssue
import com.geostamp.camera.stamp.ProofPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixCheckTest {

    @Test
    fun goodFixHasNoIssues() {
        assertTrue(FixCheck.issues(true, 2_000, 4f, false).isEmpty())
        assertNull(FixCheck.warning(emptySet(), 2_000, 4f))
    }

    @Test
    fun noFix() {
        assertEquals(setOf(FixIssue.NO_FIX), FixCheck.issues(false, null, null, null))
    }

    @Test
    fun flagsStaleInaccurateAndMock() {
        val issues = FixCheck.issues(true, 45_000, 120f, true)
        assertEquals(setOf(FixIssue.MOCK, FixIssue.STALE, FixIssue.INACCURATE), issues)
        val w = FixCheck.warning(issues, 45_000, 120f)!!
        assertTrue(w.contains("mock-location"))
        assertTrue(w.contains("45s old"))
        assertTrue(w.contains("±120 m"))
    }

    @Test
    fun thresholdsAreInclusive() {
        assertTrue(FixCheck.issues(true, FixCheck.MAX_AGE_MS, FixCheck.MAX_ACCURACY_M, false).isEmpty())
    }

    @Test
    fun formatsAge() {
        assertEquals("5s", FixCheck.formatAge(5_400))
        assertEquals("2m", FixCheck.formatAge(150_000))
        assertEquals("3h", FixCheck.formatAge(3 * 3_600_000L))
    }

    private val v1 = ProofPayload(
        version = 1, latitude = 1.0, longitude = 2.0, altitude = null, accuracyM = null,
        time = "2026-10-21T10:00:00Z", number = 1, locationSource = "phone-gps", imageSha256 = "00",
    )

    @Test
    fun proofV1CanonicalFormIsUnchanged() {
        // Photos signed before v2 must still verify.
        assertFalse(v1.copy(fixAgeS = 3, mock = true).canonical().contains("mock="))
    }

    @Test
    fun proofV2SignsFixAgeAndMock() {
        val v2 = v1.copy(version = 2, fixAgeS = 3, mock = false)
        assertTrue(v2.canonical().endsWith("\nfixAge=3\nmock=false"))
        assertEquals(v2, ProofPayload.fromJson(v2.toJson()))
    }
}
