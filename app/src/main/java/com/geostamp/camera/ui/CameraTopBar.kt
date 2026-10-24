package com.geostamp.camera.ui

import android.os.SystemClock
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.location.FixIssue
import com.geostamp.camera.ui.theme.CameraColors
import kotlinx.coroutines.delay
import java.util.Locale

/** Top row on black, like the stock camera: flash · timer · grid · quick-settings chevron … settings. */
@Composable
internal fun CameraTopBar(vm: CameraViewModel, onOpenSettings: () -> Unit) {
    val photo = vm.mode == CaptureMode.PHOTO
    val idle = !vm.isRecording && !vm.busy
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (photo) {
            BarIcon(
                icon = when (vm.flash) {
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                    else -> Icons.Filled.FlashOff
                },
                contentDescription = "Flash",
                active = vm.flash != ImageCapture.FLASH_MODE_OFF,
                onClick = vm::cycleFlash,
            )
            BarIcon(
                icon = if (vm.cam.timerSeconds > 0) Icons.Filled.Timer else Icons.Filled.TimerOff,
                contentDescription = "Timer",
                active = vm.cam.timerSeconds > 0,
                badge = vm.cam.timerSeconds.takeIf { it > 0 }?.let { "${it}s" },
                enabled = idle,
                onClick = vm::cycleTimer,
            )
        }
        BarIcon(
            icon = if (vm.cam.grid != GridType.OFF) Icons.Filled.GridOn else Icons.Filled.GridOff,
            contentDescription = "Grid",
            active = vm.cam.grid != GridType.OFF,
            onClick = vm::cycleGrid,
        )
        val quickOpen = vm.sheet == CameraSheet.QUICK_SETTINGS
        Surface(
            onClick = { vm.toggleSheet(CameraSheet.QUICK_SETTINGS) },
            shape = CircleShape,
            color = CameraColors.Scrim.copy(alpha = 0.9f),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(width = 56.dp, height = 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (quickOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Quick settings",
                    tint = if (quickOpen) MaterialTheme.colorScheme.primary else CameraColors.OnScrim,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        BarIcon(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            enabled = !vm.isRecording,
            onClick = onOpenSettings,
        )
    }
}

/** Plain icon on the black bar; it turns with the phone and can carry a small value badge. */
@Composable
private fun BarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(Modifier.upright(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> CameraColors.OnScrimDisabled
                    active -> MaterialTheme.colorScheme.primary
                    else -> CameraColors.OnScrim
                },
                modifier = Modifier.size(24.dp),
            )
            if (badge != null) {
                Text(
                    badge,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = 18.dp, start = 18.dp),
                )
            }
        }
    }
}

/** GPS accuracy and fix age; tap to ask for a fresh fix. */
@Composable
internal fun GpsStatusPill(vm: CameraViewModel, modifier: Modifier = Modifier) {
    val data by vm.repo.data.collectAsState()
    // Ticks once a second so the pill can show a fix getting old.
    val nowMs by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val fixAgeMs = data.fixElapsedMs?.let { nowMs - it }
    val fixIssues = FixCheck.issues(data.latitude != null, fixAgeMs, data.accuracyM, data.isMock)
    CameraPill(
        text = when {
            data.latitude == null -> "Searching GPS…"
            FixIssue.MOCK in fixIssues -> "Fake GPS"
            FixIssue.STALE in fixIssues && fixAgeMs != null ->
                String.format(Locale.US, "±%.0f m · %s old", data.accuracyM ?: 0f, FixCheck.formatAge(fixAgeMs))
            else -> String.format(Locale.US, "GPS ±%.0f m", data.accuracyM ?: 0f)
        },
        dotColor = when {
            data.latitude == null -> CameraColors.OnScrimDisabled
            FixIssue.MOCK in fixIssues -> CameraColors.BadFix
            fixIssues.isNotEmpty() -> CameraColors.WeakFix
            else -> CameraColors.GoodFix
        },
        contentColor = if (data.latitude != null) CameraColors.OnScrim else CameraColors.OnScrimMuted,
        onClick = vm::refreshLocation,
        modifier = modifier,
    )
}

/** Just under the viewfinder: pro controls · lens/zoom pill · move stamp. */
@Composable
internal fun ViewfinderControls(vm: CameraViewModel) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        CameraIconButton(
            icon = Icons.Filled.Tune,
            contentDescription = "Pro controls",
            active = vm.showProBar,
            onClick = vm::toggleProBar,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .upright(),
        )
        Box(Modifier.align(Alignment.Center)) { LensZoomPill(vm) }
        CameraIconButton(
            icon = Icons.Filled.SwapVert,
            contentDescription = "Move stamp",
            onClick = vm::cycleStampPosition,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .upright(),
        )
    }
}

/**
 * Lenses facing the same way as the current one, shown by zoom factor (0.6 · 1x · 2), or zoom
 * presets when the phone exposes only one lens that way. Each label turns with the phone.
 */
@Composable
private fun LensZoomPill(vm: CameraViewModel) {
    val current = vm.currentLens ?: return
    val sameSide = vm.lenses.withIndex().filter { it.value.isFront == current.isFront }
    val enabled = !vm.busy && !vm.isRecording
    if (sameSide.size > 1) {
        ZoomPill(
            labels = sameSide.map { zoomLabel(it.value.shortLabel) },
            selected = sameSide.indexOfFirst { it.index == vm.selected },
            enabled = enabled,
            onSelect = { vm.selected = sameSide[it].index },
        )
    } else if (vm.zoomMax > 1.01f) {
        val zoomMin = vm.zoomMin
        val presets = (listOf(zoomMin) + listOf(1f, 2f, 5f, 10f))
            .filter { it in zoomMin..vm.zoomMax }
            .distinctBy { (it * 10).toInt() }
        ZoomPill(
            labels = presets.map { z -> if (z < 1f) String.format(Locale.US, "%.1f", z) else "${z.toInt()}" },
            selected = presets.indexOfFirst { kotlin.math.abs(vm.zoomNow - it) < 0.05f },
            enabled = true,
            onSelect = { vm.controller.setZoomRatio(presets[it]) },
        )
    }
}

/** "Ultra-wide 0.6x" → "0.6", "Wide 1x" → "1"; a lens without a zoom hint keeps its name. */
private fun zoomLabel(shortLabel: String): String {
    val last = shortLabel.substringAfterLast(' ')
    return if (last.endsWith("x") && last.dropLast(1).toFloatOrNull() != null) last.dropLast(1) else shortLabel
}

@Composable
private fun ZoomPill(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Surface(shape = CircleShape, color = CameraColors.Scrim) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Surface(
                    onClick = { onSelect(i) },
                    enabled = enabled,
                    shape = CircleShape,
                    color = if (active) CameraColors.ScrimStrong else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (active && label.toFloatOrNull() != null) "${label}x" else label,
                            color = when {
                                active -> MaterialTheme.colorScheme.primary
                                enabled -> CameraColors.OnScrim
                                else -> CameraColors.OnScrimDisabled
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.upright(),
                        )
                    }
                }
            }
        }
    }
}

// ---- Quick settings grid (chevron) and tools sheet (pull-up handle) ----------------------------

/** Opens over the lower viewfinder; tapping outside closes it. */
@Composable
internal fun CameraSheets(
    vm: CameraViewModel,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onVoiceNote: () -> Unit,
) {
    val sheet = vm.sheet ?: return
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.sheet = null },
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CameraColors.ScrimStrong,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                // Swallow taps on the card itself so they don't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(8.dp)) {
                if (sheet == CameraSheet.MORE) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(CameraColors.OnScrimDisabled, CircleShape),
                    )
                }
                val tiles = if (sheet == CameraSheet.QUICK_SETTINGS) {
                    quickSettingsTiles(vm) {
                        vm.sheet = null
                        onOpenSettings()
                    }
                } else {
                    moreTools(vm, onVoiceNote, onOpenGallery)
                }
                TileGrid(tiles, columns = 4)
            }
        }
    }
}

private class Tile(
    val label: String,
    val icon: ImageVector,
    val on: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun TileGrid(tiles: List<Tile>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { TileButton(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileButton(t: Tile, modifier: Modifier) {
    val fg = when {
        !t.enabled -> CameraColors.OnScrimDisabled
        t.on -> MaterialTheme.colorScheme.primary
        else -> CameraColors.OnScrim
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (t.on) CameraColors.Scrim else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = t.enabled, onClick = t.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.upright().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                t.label,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (t.on) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun quickSettingsTiles(vm: CameraViewModel, openSettings: () -> Unit): List<Tile> {
    val cam = vm.cam
    val caps = vm.caps
    val bind = vm.bind
    val mode = vm.mode
    val idle = !vm.isRecording && !vm.busy
    val hdrSupported = if (mode == CaptureMode.PHOTO) caps.hdrPhoto else caps.videoColors.size > 1
    return buildList {
        add(Tile(bind?.hdrLabel ?: "HDR", Icons.Filled.HdrOn, bind?.hdrLabel != null, hdrSupported && !vm.isRecording, vm::toggleHdr))
        when (mode) {
            CaptureMode.PHOTO -> {
                add(Tile("RAW", Icons.Filled.RawOn, bind?.format != null && bind?.format != PhotoFormat.JPEG, caps.anyRaw && idle, vm::toggleRaw))
                add(Tile("Ratio ${cam.photoAspect.label}", Icons.Filled.AspectRatio, false, idle) {
                    cam.photoAspect = PhotoAspect.entries[(cam.photoAspect.ordinal + 1) % PhotoAspect.entries.size]
                })
                add(Tile(if (cam.timerSeconds > 0) "Timer ${cam.timerSeconds}s" else "Timer", Icons.Filled.Timer, cam.timerSeconds > 0, idle, vm::cycleTimer))
                add(Tile(if (cam.burstCount > 1) "Burst ${cam.burstCount}×" else "Burst", Icons.Filled.BurstMode, cam.burstCount > 1, idle, vm::cycleBurst))
            }
            CaptureMode.TIMELAPSE ->
                add(Tile("Every ${cam.timelapseIntervalSec}s", Icons.Filled.Timer, true, !vm.isTimelapseActive, vm::cycleTimelapseInterval))
            CaptureMode.SLOW_MO ->
                add(Tile("Speed ${cam.slowMoSpeed.label}", Icons.Filled.SlowMotionVideo, true, !vm.isRecording, vm::cycleSlowMoSpeed))
            CaptureMode.VIDEO -> Unit
        }
        add(Tile(if (cam.grid == GridType.OFF) "Grid" else cam.grid.label, Icons.Filled.GridOn, cam.grid != GridType.OFF, true, vm::cycleGrid))
        add(Tile("Level", Icons.Filled.Straighten, cam.level, true) { cam.level = !cam.level })
        add(Tile("Settings", Icons.Filled.Settings, false, !vm.isRecording, openSettings))
    }
}

private fun moreTools(vm: CameraViewModel, onVoiceNote: () -> Unit, onOpenGallery: () -> Unit): List<Tile> {
    val cam = vm.cam
    val caps = vm.caps
    val isRecording = vm.isRecording
    val idle = !isRecording && !vm.busy
    val photo = vm.mode == CaptureMode.PHOTO
    return buildList {
        if (photo) {
            if (caps.nightExtension) {
                add(Tile("Night", Icons.Filled.NightsStay, cam.photoExtension == ExtensionModeType.NIGHT, !isRecording) {
                    cam.photoExtension = if (cam.photoExtension == ExtensionModeType.NIGHT) ExtensionModeType.NONE else ExtensionModeType.NIGHT
                })
            }
            if (caps.bokehExtension) {
                add(Tile("Portrait", Icons.Filled.Portrait, cam.photoExtension == ExtensionModeType.BOKEH, !isRecording) {
                    cam.photoExtension = if (cam.photoExtension == ExtensionModeType.BOKEH) ExtensionModeType.NONE else ExtensionModeType.BOKEH
                })
            }
            add(Tile("Punch in", Icons.Filled.Badge, cam.attendanceMode, !isRecording, vm::toggleAttendanceMode))
            add(Tile("Selfie PiP", Icons.Filled.PictureInPicture, cam.pipMode, vm.lenses.size > 1 && !isRecording) { cam.pipMode = !cam.pipMode })
            add(Tile("Document", Icons.Filled.Description, cam.documentMode, !isRecording) { cam.documentMode = !cam.documentMode })
        }
        if (!vm.mode.isVideo) {
            add(Tile("Voice note", Icons.Filled.Mic, false, idle, onVoiceNote))
            add(Tile("Scan QR", Icons.Filled.QrCodeScanner, false, idle, vm::scanBarcode))
            add(Tile("Read sign", Icons.Filled.TextFields, false, idle, vm::scanSignboardText))
            add(Tile("AI caption", Icons.Filled.AutoAwesome, false, idle, vm::autoCaptionScene))
            if (vm.lastThumb != null) {
                add(Tile("Ghost", Icons.Filled.Layers, vm.showGhostOverlay, true) { vm.showGhostOverlay = !vm.showGhostOverlay })
            }
        }
        add(Tile("Gallery", Icons.Filled.PhotoLibrary, false, !isRecording, onOpenGallery))
    }
}
