package com.geostamp.camera.stamp

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Camera shooting parameters extracted from Camera2 live readout or EXIF metadata.
 * Formats parameters into standard photography strings e.g. "26mm  f/1.5  0.3s  ISO12800".
 */
data class ShotParams(
    val focalMm: Int? = null,
    val aperture: Float? = null,
    val shutterNs: Long? = null,
    val iso: Int? = null,
    val model: String? = null,
) {
    fun formatExifSummary(separator: String = "  "): String {
        val parts = mutableListOf<String>()
        focalMm?.let { parts.add("${it}mm") }
        aperture?.let { parts.add(String.format(Locale.US, "f/%.1f", it)) }
        shutterNs?.let { ns ->
            val sec = ns / 1_000_000_000.0
            val sText = if (sec >= 0.3) {
                String.format(Locale.US, "%.1fs", sec)
            } else {
                val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
                "1/${denom}s"
            }
            parts.add(sText)
        }
        iso?.let { parts.add("ISO$it") }
        return if (parts.isNotEmpty()) parts.joinToString(separator) else "26mm${separator}f/1.5${separator}0.3s${separator}ISO12800"
    }
}
