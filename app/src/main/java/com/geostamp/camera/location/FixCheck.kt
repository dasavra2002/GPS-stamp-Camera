package com.geostamp.camera.location

import java.util.Locale

enum class FixIssue { NO_FIX, MOCK, STALE, INACCURATE }

/** Judges whether the current location fix is good enough to stamp a photo with. */
object FixCheck {
    /** Older fixes may be from somewhere else: GPS lost indoors, or the phone has moved. */
    const val MAX_AGE_MS = 30_000L
    const val MAX_ACCURACY_M = 50f

    fun issues(hasFix: Boolean, ageMs: Long?, accuracyM: Float?, isMock: Boolean?): Set<FixIssue> {
        if (!hasFix) return setOf(FixIssue.NO_FIX)
        return buildSet {
            if (isMock == true) add(FixIssue.MOCK)
            if (ageMs != null && ageMs > MAX_AGE_MS) add(FixIssue.STALE)
            if (accuracyM != null && accuracyM > MAX_ACCURACY_M) add(FixIssue.INACCURATE)
        }
    }

    /** One-line warning for the shutter, or null when the fix is fine. */
    fun warning(issues: Set<FixIssue>, ageMs: Long?, accuracyM: Float?): String? {
        if (issues.isEmpty()) return null
        if (FixIssue.NO_FIX in issues) return "No GPS fix yet – location fields will be blank"
        val parts = buildList {
            if (FixIssue.MOCK in issues) add("location comes from a mock-location app")
            if (FixIssue.STALE in issues && ageMs != null) add("last fix is ${formatAge(ageMs)} old")
            if (FixIssue.INACCURATE in issues && accuracyM != null) add(String.format(Locale.US, "accuracy is only ±%.0f m", accuracyM))
        }
        return "Warning: " + parts.joinToString(", ") + ". This is recorded in the photo's proof."
    }

    fun formatAge(ageMs: Long): String {
        val s = ageMs / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            else -> "${s / 3600}h"
        }
    }
}
