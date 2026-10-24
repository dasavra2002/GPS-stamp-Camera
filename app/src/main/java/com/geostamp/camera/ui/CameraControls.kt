package com.geostamp.camera.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.ui.theme.CameraColors
import java.util.Locale

/** Portrait: lens/zoom and mode options, the mode switcher, then thumbnail · shutter · flip. */
@Composable
internal fun PortraitBottomDeck(vm: CameraViewModel, onShutter: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            LensOrZoomSelector(vm)
            ModeOptionChips(vm)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
        ) {
            ModeSwitcher(vm)
        }

        // Equal slots on both sides so the shutter stays centered
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                LastCaptureThumb(vm, 56.dp, showShareButton = true)
            }
            ShutterButton(vm, onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (vm.isRecording) SnapshotButton(vm, 52.dp) else FlipButton(vm, 52.dp)
            }
        }
    }
}

/** Landscape: a column on the right with the mode switcher, shutter and thumbnail. */
@Composable
internal fun LandscapeRightDeck(vm: CameraViewModel, onShutter: () -> Unit) {
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModeSwitcher(vm, horizontalPadding = 4.dp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LensOrZoomSelector(vm, allowZoomPresets = false)
            ShutterButton(vm, onShutter)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModeOptionChips(vm)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LastCaptureThumb(vm, 44.dp, showShareButton = false)
            if (vm.isRecording) SnapshotButton(vm, 44.dp) else FlipButton(vm, 44.dp)
        }
    }
}

@Composable
private fun ModeSwitcher(vm: CameraViewModel, horizontalPadding: Dp = 12.dp) {
    CaptureMode.entries.forEach { m ->
        CameraModeLabel(
            text = m.displayName,
            active = vm.mode == m,
            enabled = !vm.busy && !vm.isRecording,
            onClick = { vm.selectMode(m) },
            horizontalPadding = horizontalPadding,
        )
    }
}

@Composable
private fun LensOrZoomSelector(vm: CameraViewModel, allowZoomPresets: Boolean = true) {
    val lenses = vm.lenses
    if (lenses.size > 1) {
        CameraSegmented(
            labels = lenses.map { it.shortLabel },
            selected = vm.selected,
            onSelect = { vm.selected = it },
            enabled = !vm.busy && !vm.isRecording,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    } else if (allowZoomPresets && vm.zoomMax > 1.01f && vm.openPanel == null) {
        val zoomMin = vm.zoomMin
        val zoomMax = vm.zoomMax
        val presets = (listOf(zoomMin) + listOf(1f, 2f, 3f, 5f, 10f))
            .filter { it in zoomMin..zoomMax }
            .distinctBy { (it * 10).toInt() }
        CameraSegmented(
            labels = presets.map { z -> if (z < 1f) String.format(Locale.US, "%.1f×", z) else "${z.toInt()}×" },
            selected = presets.indexOfFirst { kotlin.math.abs(vm.zoomNow - it) < 0.05f },
            onSelect = { vm.controller.setZoomRatio(presets[it]) },
        )
    }
}

/** Mode-specific cyclers (timer and burst, time-lapse interval, slow-mo speed). */
@Composable
private fun ModeOptionChips(vm: CameraViewModel) {
    val cam = vm.cam
    val isRecording = vm.isRecording
    when (vm.mode) {
        CaptureMode.PHOTO -> {
            CameraChip(
                text = if (cam.timerSeconds > 0) "${cam.timerSeconds}s" else "Off",
                on = cam.timerSeconds > 0,
                enabled = !isRecording && !vm.busy,
                icon = Icons.Filled.Timer,
                onClick = vm::cycleTimer,
            )
            CameraChip(
                text = "${cam.burstCount}×",
                on = cam.burstCount > 1,
                enabled = !isRecording && !vm.busy,
                icon = Icons.Filled.BurstMode,
                onClick = vm::cycleBurst,
            )
        }
        CaptureMode.TIMELAPSE -> {
            CameraChip("Every ${cam.timelapseIntervalSec}s", true, !vm.isTimelapseActive, icon = Icons.Filled.Timer, onClick = vm::cycleTimelapseInterval)
        }
        CaptureMode.SLOW_MO -> {
            CameraChip(cam.slowMoSpeed.label, true, !isRecording, icon = Icons.Filled.SlowMotionVideo, onClick = vm::cycleSlowMoSpeed)
        }
        CaptureMode.VIDEO -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LastCaptureThumb(vm: CameraViewModel, size: Dp, showShareButton: Boolean) {
    val context = LocalContext.current
    var shareMenu by remember { mutableStateOf(false) }
    val lastUri = vm.lastUri
    val lastMime = vm.lastMime
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .border(1.5.dp, CameraColors.ScrimBorder, MaterialTheme.shapes.medium)
                .background(CameraColors.Scrim)
                .combinedClickable(
                    enabled = lastUri != null && !vm.isRecording,
                    onClick = { lastUri?.let { CameraIntents.openCapture(context, it, lastMime) } },
                    onLongClick = { if (!vm.isTimelapseActive) shareMenu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val t = vm.lastThumb
            if (t != null) {
                Image(t.asImageBitmap(), "Last capture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (lastUri != null) {
                Icon(
                    if (lastMime.startsWith("video")) Icons.Filled.Videocam else Icons.Filled.Photo,
                    contentDescription = "Last capture",
                    tint = CameraColors.OnScrimMuted,
                )
            } else {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = CameraColors.OnScrimDisabled, modifier = Modifier.size(22.dp))
            }
        }
        if (showShareButton && lastUri != null && !vm.isRecording && !vm.isTimelapseActive) {
            CameraIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share last capture",
                onClick = { shareMenu = true },
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
            DropdownMenuItem(
                text = { Text("Share to WhatsApp") },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    shareMenu = false
                    vm.lastUri?.let { CameraIntents.shareToWhatsApp(context, it, vm.shareText()) }
                },
            )
            DropdownMenuItem(
                text = { Text("Upload to Google Drive") },
                leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                onClick = {
                    shareMenu = false
                    vm.lastUri?.let { CameraIntents.uploadToGoogleDrive(context, it, vm.lastMime) }
                },
            )
        }
    }
}

@Composable
private fun SnapshotButton(vm: CameraViewModel, size: Dp) {
    CameraIconButton(
        icon = Icons.Filled.CameraAlt,
        contentDescription = "Take snapshot",
        enabled = !vm.busy,
        onClick = vm::takeVideoSnapshot,
        size = size,
    )
}

@Composable
private fun FlipButton(vm: CameraViewModel, size: Dp) {
    CameraIconButton(
        icon = Icons.Filled.Cameraswitch,
        contentDescription = "Flip camera",
        enabled = !vm.busy && !vm.isRecording && vm.lenses.size > 1,
        onClick = vm::flipCamera,
        size = size,
        iconSize = 24.dp,
    )
}

private val CaptureMode.displayName
    get() = when (this) {
        CaptureMode.PHOTO -> "Photo"
        CaptureMode.VIDEO -> "Video"
        CaptureMode.TIMELAPSE -> "Time-lapse"
        CaptureMode.SLOW_MO -> "Slow-mo"
    }

@Composable
private fun ShutterButton(vm: CameraViewModel, onClick: () -> Unit) {
    val mode = vm.mode
    val busy = vm.busy
    val isRecording = vm.isRecording
    val isTimelapseActive = vm.isTimelapseActive
    val isReady = vm.bind?.ok == true
    val attendanceMode = vm.cam.attendanceMode
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutterScale")

    val active = when (mode) {
        CaptureMode.PHOTO -> false
        CaptureMode.TIMELAPSE -> isTimelapseActive
        CaptureMode.VIDEO, CaptureMode.SLOW_MO -> isRecording
    }
    val fill = when {
        !isReady -> CameraColors.OnScrimDisabled
        mode == CaptureMode.PHOTO -> if (busy) CameraColors.OnScrimMuted else CameraColors.OnScrim
        mode == CaptureMode.VIDEO -> CameraColors.Recording
        mode == CaptureMode.SLOW_MO -> CameraColors.SlowMo
        else -> MaterialTheme.colorScheme.primary
    }
    val innerSize by animateDpAsState(if (active) 30.dp else 62.dp, label = "shutterInner")
    val innerCorner by animateDpAsState(if (active) 8.dp else 31.dp, label = "shutterCorner")
    val enabled = isReady && (mode != CaptureMode.PHOTO || !busy)
    val label = when (mode) {
        CaptureMode.PHOTO -> "Take photo"
        CaptureMode.TIMELAPSE -> if (isTimelapseActive) "Stop time-lapse" else "Start time-lapse"
        CaptureMode.VIDEO, CaptureMode.SLOW_MO -> if (isRecording) "Stop recording" else "Start recording"
    }

    Box(
        Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(BorderStroke(4.dp, if (isReady) CameraColors.OnScrim else CameraColors.OnScrimDisabled), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (mode == CaptureMode.PHOTO && attendanceMode) {
                Text("PUNCH", color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
        if (busy && mode == CaptureMode.PHOTO) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
