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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.stamp.BackgroundSaves
import com.geostamp.camera.ui.theme.CameraColors

/**
 * Below the viewfinder, like the stock camera: pro · lens/zoom · move stamp, a handle that pulls
 * up the tools sheet, the mode strip, then thumbnail · shutter · flip. Only the icons turn when the phone does.
 */
@Composable
internal fun CameraBottomDeck(vm: CameraViewModel, onShutter: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kept off the viewfinder so they never cover the stamp preview.
        ViewfinderControls(vm)
        SheetHandle(vm)

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            ModeSwitcher(vm)
        }

        // Equal slots on both sides so the shutter stays centered
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                LastCaptureThumb(vm, 56.dp)
            }
            ShutterButton(vm, onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (vm.isRecording) SnapshotButton(vm, 52.dp) else FlipButton(vm, 52.dp)
            }
        }
    }
}

/** Tap or swipe up to open the tools sheet, swipe down to close it. */
@Composable
private fun SheetHandle(vm: CameraViewModel) {
    val open = vm.sheet == CameraSheet.MORE
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clickable { vm.toggleSheet(CameraSheet.MORE) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -8f) vm.sheet = CameraSheet.MORE
                    else if (dragAmount > 8f && vm.sheet == CameraSheet.MORE) vm.sheet = null
                }
            }
            .semantics { contentDescription = if (open) "Close tools" else "More tools" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(if (open) MaterialTheme.colorScheme.primary else CameraColors.OnScrimDisabled, CircleShape),
        )
    }
}

@Composable
private fun ModeSwitcher(vm: CameraViewModel) {
    CaptureMode.entries.forEach { m ->
        CameraModeLabel(
            text = m.displayName,
            active = vm.mode == m,
            enabled = !vm.busy && !vm.isRecording,
            onClick = { vm.selectMode(m) },
            horizontalPadding = 14.dp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LastCaptureThumb(vm: CameraViewModel, size: Dp) {
    val context = LocalContext.current
    var shareMenu by remember { mutableStateOf(false) }
    val lastUri = vm.lastUri
    val lastMime = vm.lastMime
    val saving by BackgroundSaves.pending.collectAsState()
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .upright()
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
        // Photos still being stamped and saved in the background.
        if (saving > 0) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(size + 10.dp),
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
        modifier = Modifier.upright(),
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
        modifier = Modifier.upright(),
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
