package com.geostamp.camera

import com.geostamp.camera.camera.PhotoSpec
import com.geostamp.camera.camera.VideoSpec
import com.geostamp.camera.data.Bitrate
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.SnapshotMode
import com.geostamp.camera.data.VideoAspect
import com.geostamp.camera.data.VideoCodec
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.data.VolumeButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFeatureListTest {

    @Test
    fun testVolumeButtonActions() {
        assertEquals("Shutter", VolumeButtonAction.SHUTTER.label)
        assertEquals("Timer (2s)", VolumeButtonAction.TIMER_2S.label)
        assertEquals("Zoom", VolumeButtonAction.ZOOM.label)
        assertEquals("Volume", VolumeButtonAction.VOLUME.label)
        assertEquals(4, VolumeButtonAction.entries.size)
    }

    @Test
    fun testVideoCodecProperties() {
        assertEquals("video/avc", VideoCodec.H264.mimeType)
        assertTrue(VideoCodec.H264.label.contains("H.264"))

        assertEquals("video/hevc", VideoCodec.HEVC.mimeType)
        assertTrue(VideoCodec.HEVC.label.contains("HEVC"))
    }

    @Test
    fun testSnapshotModes() {
        assertEquals("Regular photos", SnapshotMode.REGULAR.label)
        assertEquals("Live Moments", SnapshotMode.LIVE_MOMENTS.label)
        assertEquals(2, SnapshotMode.entries.size)
    }

    @Test
    fun testPhotoSpecWithDisplayParameters() {
        val spec = PhotoSpec(
            hdr = true,
            extension = ExtensionModeType.NIGHT,
            format = PhotoFormat.JPEG,
            aspect = PhotoAspect.R4_3,
            resolution = "HIGH",
            histogram = true,
            focusPeaking = true,
            exposureVerification = true,
        )
        assertTrue(spec.focusPeaking)
        assertTrue(spec.exposureVerification)
        assertTrue(spec.histogram)
    }

    @Test
    fun testVideoSpecWithNewParameters() {
        val spec = VideoSpec(
            res = VideoRes.UHD,
            aspect = VideoAspect.R16_9,
            fps = 60,
            bitrate = Bitrate.HIGH,
            color = VideoColor.SDR,
            codec = VideoCodec.HEVC,
            autoFrameRate = true,
            stabilization = true,
            histogram = true,
            focusPeaking = true,
            exposureVerification = false,
        )
        assertEquals(VideoCodec.HEVC, spec.codec)
        assertTrue(spec.autoFrameRate)
        assertTrue(spec.focusPeaking)
        assertFalse(spec.exposureVerification)
    }
}
