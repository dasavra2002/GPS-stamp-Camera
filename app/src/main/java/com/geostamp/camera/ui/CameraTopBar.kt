package com.geostamp.camera.ui

import android.os.SystemClock
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.location.FixIssue
import com.geostamp.camera.ui.theme.CameraColors
import kotlinx.coroutines.delay
import java.util.Locale

/** Flash · GPS status · pro, tools and settings, with the scrollable quick tools row under it. */
@Composable
internal fun CameraTopBar(
    vm: CameraViewModel,
    onOpenSettings: () -> Unit,
    onVoiceNote: () -> Unit,
) {
    val mode = vm.mode
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (mode == CaptureMode.PHOTO) {
                CameraIconButton(
                    icon = when (vm.flash) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        else -> Icons.Filled.FlashOff
                    },
                    contentDescription = "Flash",
                    active = vm.flash != ImageCapture.FLASH_MODE_OFF,
                    onClick = vm::cycleFlash,
                )
            }
            // GPS status, weighted so it never crowds out the action buttons
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GpsStatusPill(vm)
            }

            CameraChip(
                text = "PRO",
                on = vm.showProBar,
                enabled = true,
                onClick = vm::toggleProBar,
            )
            CameraIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = "Quick tools",
                active = vm.showToolsBar,
                onClick = { vm.showToolsBar = !vm.showToolsBar },
            )
            CameraIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                enabled = !vm.isRecording,
                onClick = onOpenSettings,
            )
        }

        if (vm.showToolsBar) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickTools(vm, onVoiceNote)
            }
        }
    }
}

@Composable
private fun GpsStatusPill(vm: CameraViewModel) {
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
    )
}

@Composable
private fun QuickTools(vm: CameraViewModel, onVoiceNote: () -> Unit) {
    val cam = vm.cam
    val mode = vm.mode
    val caps = vm.caps
    val bind = vm.bind
    val isRecording = vm.isRecording
    val idle = !isRecording && !vm.busy

    CameraChip(
        text = if (cam.grid == GridType.OFF) "Grid" else cam.grid.label,
        on = cam.grid != GridType.OFF,
        enabled = true,
        icon = if (cam.grid != GridType.OFF) Icons.Filled.GridOn else Icons.Filled.GridOff,
        onClick = vm::cycleGrid,
    )
    val hdrSupported = if (mode == CaptureMode.PHOTO) caps.hdrPhoto else caps.videoColors.size > 1
    CameraChip("HDR", bind?.hdrLabel != null, hdrSupported && !isRecording, icon = Icons.Filled.HdrOn, onClick = vm::toggleHdr)
    if (mode == CaptureMode.PHOTO) {
        CameraChip("RAW", bind?.format != null && bind?.format != PhotoFormat.JPEG, caps.anyRaw, icon = Icons.Filled.RawOn, onClick = vm::toggleRaw)
        if (caps.nightExtension) {
            CameraChip("Night", cam.photoExtension == ExtensionModeType.NIGHT, !isRecording, icon = Icons.Filled.NightsStay, onClick = {
                cam.photoExtension = if (cam.photoExtension == ExtensionModeType.NIGHT) ExtensionModeType.NONE else ExtensionModeType.NIGHT
            })
        }
        if (caps.bokehExtension) {
            CameraChip("Portrait", cam.photoExtension == ExtensionModeType.BOKEH, !isRecording, icon = Icons.Filled.Portrait, onClick = {
                cam.photoExtension = if (cam.photoExtension == ExtensionModeType.BOKEH) ExtensionModeType.NONE else ExtensionModeType.BOKEH
            })
        }
        CameraChip("Punch in", cam.attendanceMode, !isRecording, icon = Icons.Filled.Badge, onClick = vm::toggleAttendanceMode)
        CameraChip("PiP", cam.pipMode, vm.lenses.size > 1 && !isRecording, icon = Icons.Filled.PictureInPicture, onClick = {
            cam.pipMode = !cam.pipMode
        })
        CameraChip("Document", cam.documentMode, !isRecording, icon = Icons.Filled.Description, onClick = {
            cam.documentMode = !cam.documentMode
        })
    }
    if (!mode.isVideo) {
        CameraChip("Voice note", false, idle, icon = Icons.Filled.Mic, onClick = onVoiceNote)
        CameraChip("Scan QR", false, idle, icon = Icons.Filled.QrCodeScanner, onClick = vm::scanBarcode)
        CameraChip("Read sign", false, idle, icon = Icons.Filled.TextFields, onClick = vm::scanSignboardText)
        CameraChip("AI caption", false, idle, icon = Icons.Filled.AutoAwesome, onClick = vm::autoCaptionScene)
        if (vm.lastThumb != null) {
            CameraChip("Ghost", vm.showGhostOverlay, true, icon = Icons.Filled.Layers, onClick = {
                vm.showGhostOverlay = !vm.showGhostOverlay
            })
        }
    }
}
