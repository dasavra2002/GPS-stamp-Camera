package com.geostamp.camera.location

/**
 * Decides which location source feeds the stamp when an external GNSS receiver
 * and the phone's own GPS both report fixes.
 *
 * While the receiver keeps sending usable fixes, phone fixes are ignored, so a
 * ±2 cm RTK position is never replaced by a ±5 m phone position. If the
 * receiver goes quiet for [externalTimeoutMs] (or disconnects), the phone takes over.
 */
class LocationArbiter(private val externalTimeoutMs: Long = 5_000L) {
    private var lastExternalAtMs: Long? = null

    /** Records an external fix. External fixes always win while they arrive. */
    fun onExternalFix(nowMs: Long) {
        lastExternalAtMs = nowMs
    }

    /** The receiver disconnected: hand over to the phone straight away. */
    fun onExternalLost() {
        lastExternalAtMs = null
    }

    fun externalActive(nowMs: Long): Boolean =
        lastExternalAtMs?.let { nowMs - it <= externalTimeoutMs } ?: false

    /** True if a phone fix arriving now should be used. */
    fun acceptPhoneFix(nowMs: Long): Boolean = !externalActive(nowMs)
}
