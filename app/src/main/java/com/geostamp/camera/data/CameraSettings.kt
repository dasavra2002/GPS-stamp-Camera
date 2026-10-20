package com.geostamp.camera.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class PhotoFormat(override val label: String) : Labeled {
    JPEG("JPEG"),
    RAW_JPEG("RAW + JPEG"),
    RAW("RAW only (DNG)"),
}

enum class ExtensionModeType(override val label: String) : Labeled {
    NONE("Normal"),
    HDR("HDR"),
    NIGHT("Night"),
    BOKEH("Portrait"),
    FACE_RETOUCH("Retouch"),
}

/**
 * Photo aspect ratios. 4:3 and 16:9 come straight off the sensor; the others are
 * cropped from the closest native ratio (the preview shows the crop frame).
 * [ratio] is long side / short side.
 */
enum class PhotoAspect(override val label: String, val ratio: Float, val native16x9: Boolean, val cropped: Boolean) : Labeled {
    R4_3("4:3", 4f / 3f, false, false),
    R16_9("16:9", 16f / 9f, true, false),
    R3_2("3:2", 1.5f, false, true),
    R1_1("1:1", 1f, false, true),
    R5_4("5:4", 1.25f, false, true),
    R239("2.39:1", 2.39f, true, true),
}

enum class VideoAspect(override val label: String, val ratio: Float) : Labeled {
    R16_9("16:9", 16f / 9f),
    R4_3("4:3", 4f / 3f),
}

/** Video resolution names; matched to CameraX qualities at bind time. */
enum class VideoRes(override val label: String, val baseMbps: Int) : Labeled {
    UHD("4K UHD", 50),
    FHD("1080p FHD", 20),
    HD("720p HD", 10),
    SD("480p SD", 5),
}

enum class Bitrate(override val label: String, val factor: Float) : Labeled {
    AUTO("Auto", 0f),
    STANDARD("Standard", 1f),
    HIGH("High", 1.6f),
    MAX("Max", 2.5f),
}

/** Colour / dynamic range for video (only the ones the lens supports are offered). */
enum class VideoColor(override val label: String) : Labeled {
    SDR("SDR (Rec.709)"),
    HLG("HDR HLG 10-bit"),
    HDR10("HDR10 10-bit"),
    HDR10_PLUS("HDR10+ 10-bit"),
    DOLBY_VISION("Dolby Vision 10-bit"),
}

enum class GridType(override val label: String) : Labeled { OFF("Off"), THIRDS("Thirds"), CROSSHAIR("Crosshair"), BOTH("Thirds + center") }

/** Blackmagic-style frame guides drawn over the preview (not cropped). [ratio] = width / height. */
enum class FrameGuide(override val label: String, val ratio: Float) : Labeled {
    OFF("Off", 0f),
    R239("2.39:1", 2.39f),
    R2("2:1", 2f),
    R185("1.85:1", 1.85f),
    R1("1:1", 1f),
    R4_5("4:5", 0.8f),
    R9_16("9:16", 9f / 16f),
}

enum class VolumeButtonAction(override val label: String) : Labeled {
    SHUTTER("Shutter"),
    TIMER_2S("Timer (2s)"),
    ZOOM("Zoom"),
    VOLUME("Volume"),
}

enum class VideoCodec(override val label: String, val mimeType: String) : Labeled {
    H264("H.264 (High compatibility)", "video/avc"),
    HEVC("HEVC (High efficiency)", "video/hevc"),
}

enum class SnapshotMode(override val label: String) : Labeled {
    REGULAR("Regular photos"),
    LIVE_MOMENTS("Live Moments"),
}

/** Pro camera options, remembered between launches. */
class CameraSettings(context: Context) {
    private val prefs = context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)

    // Photo
    var photoFormat by enumPref("photo_format", PhotoFormat.JPEG, PhotoFormat.entries)
    var photoAspect by enumPref("photo_aspect", PhotoAspect.R4_3, PhotoAspect.entries)
    /** "max" or "WIDTHxHEIGHT". */
    var photoResolution by stringPref("photo_res", "max")
    var hdrPhoto by boolPref("hdr_photo", false)
    var photoExtension by enumPref("photo_ext", ExtensionModeType.NONE, ExtensionModeType.entries)
    var timerSeconds by intPref("timer_sec", 0)
    var burstCount by intPref("burst_count", 1)
    var timelapseIntervalSec by intPref("timelapse_interval_sec", 2)
    var autoDriveUpload by boolPref("auto_drive_upload", false)
    var ghostOverlay by boolPref("ghost_overlay", false)
    var pipMode by boolPref("pip_mode", false)
    var documentMode by boolPref("document_mode", false)
    var attendanceMode by boolPref("attendance_mode", false)

    // Video
    var videoRes by enumPref("video_res", VideoRes.FHD, VideoRes.entries)
    var videoAspect by enumPref("video_aspect", VideoAspect.R16_9, VideoAspect.entries)
    var fps by intPref("fps", 30)
    var bitrate by enumPref("bitrate", Bitrate.AUTO, Bitrate.entries)
    var videoColor by enumPref("video_color", VideoColor.SDR, VideoColor.entries)
    var videoCodec by enumPref("video_codec", VideoCodec.H264, VideoCodec.entries)
    var stabilization by boolPref("stabilization", true)
    var snapshotMode by enumPref("snapshot_mode", SnapshotMode.REGULAR, SnapshotMode.entries)
    var autoFrameRate by boolPref("auto_frame_rate", false)
    var allowVideoTagging by boolPref("allow_video_tagging", true)
    var motionTrackingFocus by boolPref("motion_tracking_focus", false)

    // Hardware Controls
    var volumeButtonAction by enumPref("volume_action", VolumeButtonAction.SHUTTER, VolumeButtonAction.entries)

    // Monitoring overlays
    var grid by enumPref("grid", GridType.OFF, GridType.entries)
    var frameGuide by enumPref("guide", FrameGuide.OFF, FrameGuide.entries)
    var histogram by boolPref("histogram", true)
    var level by boolPref("level", true)
    var focusPeaking by boolPref("focus_peaking", false)
    var exposureVerification by boolPref("exposure_verification", false)

    // Preserve Settings toggles
    var preserveCameraMode by boolPref("preserve_mode", true)
    var preservePortraitFocalLength by boolPref("preserve_portrait_focal", true)
    var preserveLiveMoments by boolPref("preserve_live_moments", true)
    var preserveBeautify by boolPref("preserve_beautify", true)
    var preserveFilters by boolPref("preserve_filters", true)
    var preserveAiCamera by boolPref("preserve_ai_camera", true)
    var preserveAutoNightMode by boolPref("preserve_auto_night", true)
    var preserveExposureCompensation by boolPref("preserve_ev", true)
    var preserveParameters by boolPref("preserve_params", true)

    // Saved state values for preserve
    var savedCameraMode by stringPref("saved_camera_mode", "PHOTO")
    var savedLensIndex by intPref("saved_lens_index", 0)
    var savedEv by intPref("saved_ev", 0)
    var savedIso by intPref("saved_iso", -1)
    var savedShutterNs by longPref("saved_shutter_ns", -1L)
    var savedWb by stringPref("saved_wb", "AUTO")
    var savedKelvin by intPref("saved_kelvin", 5600)
    var savedFocusDiopters by floatPref("saved_focus_diopters", -1f)

    // ---- tiny persisted-state delegates ----
    private fun <E : Enum<E>> enumPref(key: String, def: E, values: List<E>) = object : ReadWriteProperty<Any?, E> {
        private val state = mutableStateOf(values.firstOrNull { it.name == prefs.getString(key, null) } ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>): E = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: E) {
            state.value = value
            prefs.edit().putString(key, value.name).apply()
        }
    }

    private fun boolPref(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private val state = mutableStateOf(prefs.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun intPref(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        private val state = mutableStateOf(prefs.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun stringPref(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        private val state = mutableStateOf(prefs.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    private fun longPref(key: String, def: Long) = object : ReadWriteProperty<Any?, Long> {
        private val state = mutableStateOf(prefs.getLong(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            state.value = value
            prefs.edit().putLong(key, value).apply()
        }
    }

    private fun floatPref(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        private val state = mutableStateOf(prefs.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            state.value = value
            prefs.edit().putFloat(key, value).apply()
        }
    }
}
