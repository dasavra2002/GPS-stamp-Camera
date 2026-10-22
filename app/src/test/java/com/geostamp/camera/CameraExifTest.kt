package com.geostamp.camera

import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.stamp.CameraExif
import com.geostamp.camera.stamp.ShotParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CameraExifTest {

    @Test
    fun gpsTimeIsUtc() {
        // 01:30 at +05:30 is 20:00 UTC the previous day.
        val t = ZonedDateTime.of(2026, 10, 21, 1, 30, 15, 0, ZoneOffset.ofHoursMinutes(5, 30))
        val (date, time) = CameraExif.gpsDateTime(t.toInstant().toEpochMilli())
        assertEquals("2026:10:20", date)
        assertEquals("20/1,0/1,15/1", time)
    }

    @Test
    fun positioningErrorIsCentimetreRational() {
        assertEquals("350/100", CameraExif.rationalMetres(3.5f))
        assertEquals("2/100", CameraExif.rationalMetres(0.02f))
    }

    @Test
    fun shotParamsFallback() {
        val tags = CameraExif.fromShotParams(ShotParams(focalMm = 26, aperture = 1.8f, shutterNs = 10_000_000L, iso = 200))
        assertEquals("0.01", tags[ExifInterface.TAG_EXPOSURE_TIME])
        assertEquals("180/100", tags[ExifInterface.TAG_F_NUMBER])
        assertEquals("200", tags[ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY])
        assertEquals("26", tags[ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM])
        assertTrue(CameraExif.fromShotParams(null).isEmpty())
    }

    @Test
    fun orientationAndSizeAreNotCarried() {
        // The stamped photo is upright and may be cropped, so these must come from the new file.
        assertFalse(ExifInterface.TAG_ORIENTATION in CameraExif.CARRIED_TAGS)
        assertFalse(ExifInterface.TAG_PIXEL_X_DIMENSION in CameraExif.CARRIED_TAGS)
        assertFalse(ExifInterface.TAG_GPS_LATITUDE in CameraExif.CARRIED_TAGS)
    }
}
