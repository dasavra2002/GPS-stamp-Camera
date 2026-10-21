package com.geostamp.camera.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.camera.LensCaps
import com.geostamp.camera.camera.Manual
import com.geostamp.camera.camera.ManualState
import com.geostamp.camera.camera.Readout
import com.geostamp.camera.camera.SensorInfo
import com.geostamp.camera.camera.WbMode
import com.geostamp.camera.data.Bitrate
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.FrameGuide
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.VideoAspect
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

enum class Panel { FPS, SHUTTER, ISO, EV, WB, FOCUS, FORMAT, DISPLAY }

private val Mono = FontFamily.Monospace

// ------------------------------------------------------------------------------------------
// Info bar (top), modelled on Blackmagic Camera: each value is tappable.
// ------------------------------------------------------------------------------------------

@Composable
fun InfoBar(
    mode: CaptureMode,
    manual: ManualState,
    readout: Readout,
    cam: CameraSettings,
    sensor: SensorInfo,
    formatText: String,
    open: Panel?,
    enabled: Boolean,
    onOpen: (Panel?) -> Unit,
) {
    val fps = cam.fps
    val shutterNs = manual.shutterNs ?: readout.exposureNs
    val shutterText = when {
        shutterNs == null -> "--"
        mode.isVideo -> Manual.angleLabel((Manual.nsToAngle(shutterNs, fps) * 10).roundToInt() / 10f)
        else -> Manual.shutterLabel(shutterNs)
    }
    val iso = manual.iso ?: readout.iso
    val wbText = when (manual.wb) {
        WbMode.AUTO -> "AWB"
        WbMode.KELVIN -> "${manual.kelvin}K"
        else -> "${manual.wb.nominalK}K"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            @Composable
            fun seg(title: String, value: String, isManual: Boolean, panel: Panel) {
                val active = open == panel
                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .clickable(enabled = enabled) { onOpen(if (active) null else panel) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title + if (!isManual) " A" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontFamily = Mono,
                    )
                    Text(
                        value,
                        color = if (isManual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Mono,
                        maxLines = 1,
                    )
                }
            }
            if (mode.isVideo) seg("FPS", "$fps", true, Panel.FPS)
            seg(if (mode.isVideo) "SHUTTER" else "SPEED", shutterText, manual.shutterNs != null, Panel.SHUTTER)
            seg("ISO", iso?.toString() ?: "--", manual.iso != null, Panel.ISO)
            if (!manual.exposureManual) seg("EV", String.format(Locale.US, "%+d", manual.ev), manual.ev != 0, Panel.EV)
            seg("WB", wbText, manual.wb != WbMode.AUTO, Panel.WB)
            if (manual.wb == WbMode.KELVIN) seg("TINT", String.format(Locale.US, "%+d", manual.tint), true, Panel.WB)
            if (sensor.manualFocus || manual.focusDiopters != null) {
                seg("FOCUS", Manual.focusLabel(manual.focusDiopters), manual.focusDiopters != null, Panel.FOCUS)
            }
            seg("FORMAT", formatText, true, Panel.FORMAT)
            seg("VIEW", "▦", true, Panel.DISPLAY)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Control panels (bottom), one per info-bar value.
// ------------------------------------------------------------------------------------------

@Composable
fun ProChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Mono,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = Mono,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    )
}

@Composable
private fun ProSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

@Composable
fun ControlPanel(
    panel: Panel,
    mode: CaptureMode,
    manual: ManualState,
    readout: Readout,
    sensor: SensorInfo,
    caps: LensCaps,
    cam: CameraSettings,
    evRange: IntRange?,
    photoSizes: List<Size>,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            when (panel) {
            Panel.FPS -> {
                PanelLabel("FRAME RATE")
                ChipRow { sensor.fpsOptions.forEach { f -> ProChip("$f", cam.fps == f) { cam.fps = f } } }
            }

            Panel.SHUTTER -> {
                if (!sensor.manualSensor) {
                    PanelLabel("Manual shutter isn't supported on this lens")
                } else if (mode.isVideo) {
                    PanelLabel("SHUTTER ANGLE  (180° = natural motion blur)")
                    ChipRow {
                        ProChip("AUTO", manual.shutterNs == null) { manual.shutterNs = null }
                        Manual.SHUTTER_ANGLES.forEach { a ->
                            val ns = Manual.angleToNs(a, cam.fps)
                            val ok = sensor.exposureRange?.contains(ns) ?: true
                            val sel = manual.shutterNs?.let { abs(Manual.nsToAngle(it, cam.fps) - a) < 0.5f } ?: false
                            ProChip(Manual.angleLabel(a), sel, ok) { manual.shutterNs = ns }
                        }
                    }
                } else {
                    PanelLabel("SHUTTER SPEED")
                    ChipRow {
                        ProChip("AUTO", manual.shutterNs == null) { manual.shutterNs = null }
                        Manual.SHUTTER_SPEEDS.forEach { s ->
                            val ns = Manual.speedToNs(s)
                            val ok = sensor.exposureRange?.contains(ns) ?: true
                            ProChip(if (s > 0) "1/$s" else "${-s}s", manual.shutterNs == ns, ok) { manual.shutterNs = ns }
                        }
                    }
                }
            }

            Panel.ISO -> {
                if (!sensor.manualSensor) {
                    PanelLabel("Manual ISO isn't supported on this lens")
                } else {
                    PanelLabel("ISO  " + (sensor.isoRange?.let { "(${it.lower}–${it.upper})" } ?: ""))
                    ChipRow {
                        ProChip("AUTO", manual.iso == null) { manual.iso = null }
                        Manual.ISO_STOPS.filter { s -> sensor.isoRange?.contains(s) ?: true }.forEach { s ->
                            ProChip("$s", manual.iso == s) { manual.iso = s }
                        }
                    }
                }
            }

            Panel.EV -> {
                PanelLabel("EXPOSURE COMPENSATION  ${String.format(Locale.US, "%+d", manual.ev)}")
                if (evRange == null || evRange.first == evRange.last) {
                    PanelLabel("Not supported on this lens")
                } else {
                    ProSlider(manual.ev.toFloat(), evRange.first.toFloat()..evRange.last.toFloat(), (evRange.last - evRange.first - 1).coerceAtLeast(0)) {
                        manual.ev = it.roundToInt()
                    }
                }
            }

            Panel.WB -> {
                PanelLabel("WHITE BALANCE")
                ChipRow {
                    WbMode.entries.forEach { m ->
                        val ok = when (m) {
                            WbMode.AUTO -> true
                            WbMode.KELVIN -> sensor.manualWb
                            else -> m.awbMode in sensor.awbModes
                        }
                        ProChip(if (m.nominalK > 0) "${m.label} ${m.nominalK}K" else m.label, manual.wb == m, ok) { manual.wb = m }
                    }
                }
                if (manual.wb == WbMode.KELVIN) {
                    PanelLabel("TEMPERATURE  ${manual.kelvin}K")
                    ProSlider(manual.kelvin.toFloat(), 2000f..10000f) { manual.kelvin = (it / 50).roundToInt() * 50 }
                    PanelLabel("TINT  ${String.format(Locale.US, "%+d", manual.tint)}")
                    ProSlider(manual.tint.toFloat(), -50f..50f) { manual.tint = it.roundToInt() }
                }
            }

            Panel.FOCUS -> {
                PanelLabel("FOCUS  " + Manual.focusLabel(manual.focusDiopters))
                ChipRow {
                    ProChip("AF", manual.focusDiopters == null) { manual.focusDiopters = null }
                    ProChip("∞", manual.focusDiopters == 0f, sensor.manualFocus) { manual.focusDiopters = 0f }
                    readout.focusDiopters?.let { d ->
                        ProChip("Lock " + Manual.focusLabel(d), false, sensor.manualFocus) { manual.focusDiopters = d }
                    }
                }
                if (sensor.manualFocus) {
                    // Left = infinity, right = closest focus.
                    ProSlider(manual.focusDiopters ?: readout.focusDiopters ?: 0f, 0f..sensor.minFocusDiopters) {
                        manual.focusDiopters = it
                    }
                }
            }

            Panel.FORMAT -> if (!mode.isVideo) {
                PanelLabel("FORMAT")
                ChipRow {
                    PhotoFormat.entries.forEach { f ->
                        val ok = when (f) {
                            PhotoFormat.JPEG -> true
                            PhotoFormat.RAW_JPEG -> caps.rawJpeg
                            PhotoFormat.RAW -> caps.rawOnly || caps.rawJpeg
                        }
                        ProChip(f.label, cam.photoFormat == f, ok) { cam.photoFormat = f }
                    }
                    ProChip(
                        "HDR" + if (caps.hdrExtension) "" else if (caps.ultraHdrPhoto) " (Ultra)" else "",
                        cam.hdrPhoto, caps.hdrPhoto,
                    ) { cam.hdrPhoto = !cam.hdrPhoto }
                }
                PanelLabel("ASPECT RATIO")
                ChipRow { PhotoAspect.entries.forEach { a -> ProChip(a.label, cam.photoAspect == a) { cam.photoAspect = a } } }
                PanelLabel("RESOLUTION")
                ChipRow {
                    ProChip("MAX", cam.photoResolution == "max") { cam.photoResolution = "max" }
                    photoSizes.forEach { s ->
                        val key = "${s.width}x${s.height}"
                        val mp = s.width.toLong() * s.height / 1_000_000.0
                        ProChip(String.format(Locale.US, "%.0fMP %d×%d", mp, s.width, s.height), cam.photoResolution == key) {
                            cam.photoResolution = key
                        }
                    }
                }
            } else {
                PanelLabel("RESOLUTION")
                ChipRow {
                    VideoRes.entries.forEach { r -> ProChip(r.label, cam.videoRes == r, r in caps.videoResolutions) { cam.videoRes = r } }
                }
                PanelLabel("ASPECT RATIO   (frame guides: VIEW)")
                ChipRow { VideoAspect.entries.forEach { a -> ProChip(a.label, cam.videoAspect == a) { cam.videoAspect = a } } }
                PanelLabel("COLOUR / DYNAMIC RANGE")
                ChipRow {
                    VideoColor.entries.forEach { c -> ProChip(c.label, cam.videoColor == c, c in caps.videoColors) { cam.videoColor = c } }
                }
                PanelLabel("BIT RATE")
                ChipRow { Bitrate.entries.forEach { b -> ProChip(b.label, cam.bitrate == b) { cam.bitrate = b } } }
                ChipRow {
                    ProChip("Stabilization " + if (cam.stabilization) "ON" else "OFF", cam.stabilization) {
                        cam.stabilization = !cam.stabilization
                    }
                }
            }

            Panel.DISPLAY -> {
                PanelLabel("GRID")
                ChipRow { GridType.entries.forEach { g -> ProChip(g.label, cam.grid == g) { cam.grid = g } } }
                PanelLabel("FRAME GUIDES")
                ChipRow { FrameGuide.entries.forEach { g -> ProChip(g.label, cam.frameGuide == g) { cam.frameGuide = g } } }
                ChipRow {
                    ProChip("Histogram", cam.histogram) { cam.histogram = !cam.histogram }
                    ProChip("Level", cam.level) { cam.level = !cam.level }
                    ProChip("Reset manual", false) { manual.resetAll() }
                }
            }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Monitoring overlays drawn over the preview.
// ------------------------------------------------------------------------------------------

@Composable
fun GridOverlay(grid: GridType, modifier: Modifier = Modifier) {
    if (grid == GridType.OFF) return
    Canvas(modifier.fillMaxSize()) {
        val c = Color(0x88FFFFFF)
        val sw = 1.dp.toPx()
        if (grid == GridType.THIRDS || grid == GridType.BOTH) {
            for (i in 1..2) {
                drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), sw)
                drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), sw)
            }
        }
        if (grid == GridType.CROSSHAIR || grid == GridType.BOTH) {
            val l = 14.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(c, Offset(cx - l, cy), Offset(cx + l, cy), sw * 1.5f)
            drawLine(c, Offset(cx, cy - l), Offset(cx, cy + l), sw * 1.5f)
        }
    }
}

/**
 * Darkens everything outside a centred frame of [ratio] (width / height of the frame on screen).
 * [solid] = true draws an opaque mask (photo crop), false a translucent guide.
 */
@Composable
fun FrameMask(ratio: Float, solid: Boolean, modifier: Modifier = Modifier) {
    if (ratio <= 0f) return
    Canvas(modifier.fillMaxSize()) {
        val boxRatio = size.width / size.height
        val (w, h) = if (ratio > boxRatio) size.width to size.width / ratio else size.height * ratio to size.height
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val shade = if (solid) Color(0xCC000000) else Color(0x66000000)
        drawRect(shade, Offset.Zero, GSize(size.width, top))
        drawRect(shade, Offset(0f, top + h), GSize(size.width, size.height - top - h))
        drawRect(shade, Offset(0f, top), GSize(left, h))
        drawRect(shade, Offset(left + w, top), GSize(size.width - left - w, h))
        drawRect(Color(0xAAFFFFFF), Offset(left, top), GSize(w, h), style = Stroke(1.dp.toPx()))
    }
}

/** Live phone roll in degrees (0 = level when held upright). */
@Composable
fun rememberRollDegrees(enabled: Boolean): Float {
    val context = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                // Upright portrait: gravity along +y. Landscape: along ±x.
                var deg = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                // Snap the reference to the nearest 90° so landscape shooting also reads 0.
                val ref = (deg / 90f).roundToInt() * 90f
                deg -= ref
                roll = roll * 0.8f + deg * 0.2f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }
    return roll
}

@Composable
fun LevelIndicator(roll: Float, modifier: Modifier = Modifier) {
    val level = abs(roll) < 1f
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.width * 0.18f
        val color = if (level) Color(0xFF66BB6A) else Color(0xCCFFFFFF)
        // fixed reference ticks
        drawLine(Color(0x88FFFFFF), Offset(cx - half - 16.dp.toPx(), cy), Offset(cx - half - 4.dp.toPx(), cy), 2.dp.toPx())
        drawLine(Color(0x88FFFFFF), Offset(cx + half + 4.dp.toPx(), cy), Offset(cx + half + 16.dp.toPx(), cy), 2.dp.toPx())
        rotate(-roll, Offset(cx, cy)) {
            drawLine(color, Offset(cx - half, cy), Offset(cx + half, cy), 2.dp.toPx())
        }
    }
}

@Composable
fun HistogramView(bins: IntArray?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 84.dp, height = 32.dp)
            .background(Color(0x80000000), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(2.5.dp),
    ) {
        if (bins == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val bw = size.width / bins.size
            val path = Path().apply {
                moveTo(0f, size.height)
                bins.forEachIndexed { i, v ->
                    // square-root scale keeps small values visible
                    val h = kotlin.math.sqrt(v / max) * size.height
                    lineTo(i * bw, size.height - h)
                    lineTo((i + 1) * bw, size.height - h)
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, Color(0xB3FFFFFF))
            // clipping warnings
            if (bins.first() > max * 0.5f) drawRect(Color(0xFF42A5F5), Offset.Zero, GSize(2.dp.toPx(), size.height))
            if (bins.last() > max * 0.5f) drawRect(Color(0xFFEF5350), Offset(size.width - 2.dp.toPx(), 0f), GSize(2.dp.toPx(), size.height))
        }
    }
}

@Composable
fun RecTimer(seconds: Long, modifier: Modifier = Modifier) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    Row(
        modifier
            .background(Color(0xCCB71C1C), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("● REC ", color = Color.White, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Text(String.format(Locale.US, "%02d:%02d:%02d", h, m, s), color = Color.White, fontSize = 12.sp, fontFamily = Mono)
    }
}
