package com.geostamp.camera.stamp

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Camera EXIF (exposure, lens, flash, ...) carried over from the camera's JPEG to the
 * stamped photo, which is re-encoded from a bitmap and would otherwise lose it.
 */
object CameraExif {
    /** Shooting tags worth keeping. Orientation and image size are left out: the stamped photo is upright and may be cropped. */
    val CARRIED_TAGS = listOf(
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    )

    fun read(jpeg: ByteArray): Map<String, String> = try {
        read(ExifInterface(ByteArrayInputStream(jpeg)))
    } catch (e: Exception) {
        emptyMap()
    }

    fun read(input: InputStream): Map<String, String> = try {
        read(ExifInterface(input))
    } catch (e: Exception) {
        emptyMap()
    }

    fun read(file: File): Map<String, String> = try {
        read(ExifInterface(file))
    } catch (e: Exception) {
        emptyMap()
    }

    private fun read(exif: ExifInterface): Map<String, String> =
        CARRIED_TAGS.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()

    /** Fallback when the camera JPEG had no EXIF: tags from the live Camera2 readout. */
    fun fromShotParams(p: ShotParams?): Map<String, String> {
        if (p == null) return emptyMap()
        return buildMap {
            p.shutterNs?.let { put(ExifInterface.TAG_EXPOSURE_TIME, String.format(Locale.US, "%.9f", it / 1e9).trimEnd('0').trimEnd('.')) }
            p.aperture?.let { put(ExifInterface.TAG_F_NUMBER, "${(it * 100).roundToInt()}/100") }
            p.iso?.let { put(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
            p.focalMm?.let { put(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, it.toString()) }
        }
    }

    /** GPS date and time stamp (always UTC) of a fix measured at [fixEpochMs]. */
    fun gpsDateTime(fixEpochMs: Long): Pair<String, String> {
        val utc = Instant.ofEpochMilli(fixEpochMs).atOffset(ZoneOffset.UTC)
        val date = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.US).format(utc)
        val time = String.format(Locale.US, "%d/1,%d/1,%d/1", utc.hour, utc.minute, utc.second)
        return date to time
    }

    /** EXIF rational for a horizontal positioning error in metres, at centimetre precision. */
    fun rationalMetres(m: Float): String = "${(m * 100.0).roundToLong()}/100"
}
