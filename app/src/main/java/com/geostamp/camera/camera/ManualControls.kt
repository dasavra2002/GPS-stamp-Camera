package com.geostamp.camera.camera

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geostamp.camera.data.Labeled
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** White balance presets plus manual Kelvin, like the Blackmagic camera app. */
enum class WbMode(override val label: String, val awbMode: Int, val nominalK: Int) : Labeled {
    AUTO("Auto", CaptureRequest.CONTROL_AWB_MODE_AUTO, 0),
    TUNGSTEN("Tungsten", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, 3200),
    FLUORESCENT("Fluorescent", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, 4000),
    DAYLIGHT("Daylight", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, 5600),
    CLOUDY("Cloudy", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, 6500),
    SHADE("Shade", CaptureRequest.CONTROL_AWB_MODE_SHADE, 7500),
    KELVIN("Kelvin", CaptureRequest.CONTROL_AWB_MODE_OFF, 0),
}

/** What the sensor of a lens allows. */
data class SensorInfo(
    val isoRange: Range<Int>? = null,
    val exposureRange: Range<Long>? = null,
    val minFocusDiopters: Float = 0f,
    val manualSensor: Boolean = false,
    val manualWb: Boolean = false,
    val awbModes: Set<Int> = emptySet(),
    val photoSizes43: List<Size> = emptyList(),
    val photoSizes169: List<Size> = emptyList(),
    val fpsOptions: List<Int> = emptyList(),
) {
    val manualFocus get() = minFocusDiopters > 0f
}

/** Live values reported by the camera every frame (shown in the info bar even in Auto). */
data class Readout(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val frameDurationNs: Long? = null,
    val gains: RggbChannelVector? = null,
    val transform: ColorSpaceTransform? = null,
)

/** Manual settings chosen by the user; null means automatic. Not persisted, like a real camera's session. */
class ManualState {
    var iso by mutableStateOf<Int?>(null)
    var shutterNs by mutableStateOf<Long?>(null)
    var wb by mutableStateOf(WbMode.AUTO)
    var kelvin by mutableStateOf(5600)
    var tint by mutableStateOf(0)
    var focusDiopters by mutableStateOf<Float?>(null)
    var ev by mutableStateOf(0)

    val exposureManual get() = iso != null || shutterNs != null

    fun resetAll() {
        iso = null; shutterNs = null; wb = WbMode.AUTO; kelvin = 5600; tint = 0; focusDiopters = null; ev = 0
    }
}

object Manual {
    val ISO_STOPS = listOf(25, 50, 64, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 12800)
    /** Shutter speeds as 1/x seconds (photo mode). Values > 0 are fractions, < 0 are whole seconds. */
    val SHUTTER_SPEEDS = listOf(8000, 4000, 2000, 1000, 500, 250, 125, 100, 60, 50, 30, 25, 15, 8, 4, 2, 1, -2, -4, -8)
    /** Shutter angles (video mode). 180° is the cinematic standard. */
    val SHUTTER_ANGLES = listOf(11.25f, 22.5f, 45f, 90f, 144f, 172.8f, 180f, 270f, 360f)

    fun speedToNs(s: Int): Long = if (s > 0) 1_000_000_000L / s else -s * 1_000_000_000L
    fun angleToNs(angle: Float, fps: Int): Long = (angle / 360f / fps.coerceAtLeast(1) * 1e9f).toLong()
    fun nsToAngle(ns: Long, fps: Int): Float = ns / 1e9f * fps * 360f

    fun shutterLabel(ns: Long): String {
        if (ns >= 1_000_000_000L) return String.format(Locale.US, "%.1fs", ns / 1e9)
        val denom = (1e9 / ns).roundToInt()
        return "1/$denom"
    }

    fun angleLabel(angle: Float): String =
        if (angle == angle.roundToInt().toFloat()) "${angle.roundToInt()}°" else String.format(Locale.US, "%.1f°", angle)

    fun focusLabel(diopters: Float?): String = when {
        diopters == null -> "AF"
        diopters <= 0.01f -> "∞"
        else -> String.format(Locale.US, "%.2fm", 1f / diopters)
    }

    /**
     * Approximate white-balance gains for a light of [kelvin] colour temperature
     * (blackbody colour approximation), with green/magenta [tint] (-50…+50).
     */
    fun kelvinGains(kelvin: Int, tint: Int): RggbChannelVector {
        val t = kelvin.coerceIn(1500, 15000) / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 1.0
            else -> 138.5177312231 * ln(t - 10) - 305.0447927307
        }
        val rc = r.coerceIn(1.0, 255.0)
        val gc = g.coerceIn(1.0, 255.0)
        val bc = b.coerceIn(1.0, 255.0)
        var rG = (gc / rc).toFloat()
        var gG = (1.0 - tint * 0.006).toFloat() // + tint = magenta (less green)
        var bG = (gc / bc).toFloat()
        val lowest = min(rG, min(gG, bG))
        rG /= lowest; gG /= lowest; bG /= lowest
        return RggbChannelVector(rG, gG, gG, bG)
    }

    /** Camera2 request options for the current manual settings. */
    fun options(state: ManualState, info: SensorInfo, readout: Readout, fps: Int): CaptureRequestOptions {
        val b = CaptureRequestOptions.Builder()

        if (state.exposureManual && info.manualSensor) {
            val iso = (state.iso ?: readout.iso ?: 100).let { v -> info.isoRange?.let { v.coerceIn(it.lower, it.upper) } ?: v }
            val exp = (state.shutterNs ?: readout.exposureNs ?: 16_666_666L).let { v ->
                info.exposureRange?.let { v.coerceIn(it.lower, it.upper) } ?: v
            }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, max(exp, 1_000_000_000L / fps.coerceAtLeast(1)))
        }

        when (state.wb) {
            WbMode.AUTO -> Unit
            WbMode.KELVIN -> if (info.manualWb) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinGains(state.kelvin, state.tint))
                readout.transform?.let { b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, it) }
            }
            else -> if (state.wb.awbMode in info.awbModes) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, state.wb.awbMode)
            }
        }

        val focus = state.focusDiopters
        if (focus != null && info.manualFocus) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus.coerceIn(0f, info.minFocusDiopters))
        }
        return b.build()
    }
}
