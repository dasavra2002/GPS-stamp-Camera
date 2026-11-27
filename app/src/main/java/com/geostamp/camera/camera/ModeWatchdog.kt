package com.geostamp.camera.camera

/**
 * Decides whether a RAW / HDR mode really started. CameraX can accept a mode when binding and
 * only fail once the camera session starts, which shows up as a black or frozen viewfinder.
 */
object ModeWatchdog {
    /** How long a special mode gets to show its first preview frame (extensions can be slow). */
    const val START_TIMEOUT_MS = 6_000L
    /** Ignore the viewfinder state just after binding: it can still show the previous session. */
    const val SETTLE_MS = 700L
    const val POLL_MS = 150L

    enum class Verdict { WAIT, OK, FAILED }

    /**
     * [streaming]: the viewfinder shows frames. [cameraOpen]: the camera reports OPEN.
     * [fatalError]: the camera reported a stream-configuration or fatal error.
     */
    fun verdict(elapsedMs: Long, streaming: Boolean, cameraOpen: Boolean, fatalError: Boolean): Verdict = when {
        fatalError -> Verdict.FAILED
        elapsedMs < SETTLE_MS -> Verdict.WAIT
        streaming && cameraOpen -> Verdict.OK
        elapsedMs >= START_TIMEOUT_MS -> Verdict.FAILED
        else -> Verdict.WAIT
    }

    /** Message shown after falling back to a normal JPEG. */
    fun fallbackMessage(mode: SpecialMode, lensName: String): String =
        "${mode.label} couldn't start on the $lensName camera of this phone, so it was turned off for this lens"
}
