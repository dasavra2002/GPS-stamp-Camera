package com.geostamp.camera.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.FrameGuide
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.ui.theme.CameraColors

/** The live preview with every overlay drawn on it: guides, stamp preview, meters and status. */
@Composable
internal fun CameraViewfinder(
    vm: CameraViewModel,
    config: StampConfig,
    modifier: Modifier = Modifier,
) {
    val cam = vm.cam
    val controller = vm.controller
    val focusPeakingBmp by controller.focusPeakingBitmap.collectAsState()
    val exposureVerificationBmp by controller.exposureVerificationBitmap.collectAsState()
    val histogram by controller.histogram.collectAsState()
    val overlayTop = 8.dp

    BoxWithConstraints(modifier) {
        CameraPreview(controller, cam)

        if (cam.focusPeaking) focusPeakingBmp?.let { FullFrameImage(it, "Focus Peaking", 0.9f) }
        if (cam.exposureVerification) exposureVerificationBmp?.let { FullFrameImage(it, "Exposure Verification", 0.75f) }
        val ghost = vm.lastThumb
        if (vm.showGhostOverlay && ghost != null && vm.mode == CaptureMode.PHOTO) {
            FullFrameImage(ghost, "Ghost alignment overlay", vm.ghostAlpha, ContentScale.Crop)
        }

        GridOverlay(cam.grid)
        // The screen is always portrait and the sensor region is the same however the phone is
        // held, so crops and guides are always drawn tall.
        if (vm.photoCrop) FrameMask(1f / cam.photoAspect.ratio, solid = true)
        if (cam.frameGuide != FrameGuide.OFF) FrameMask(1f / cam.frameGuide.ratio, solid = false)
        if (cam.documentMode) DocumentGuide()

        // Stamp preview, inside the crop frame when a cropped photo format is chosen. Held
        // sideways, the photo is landscape: the stamp is drawn for that frame and turned with the phone.
        Box(
            Modifier
                .align(Alignment.Center)
                .aspectRatio(1f / vm.frameLongShort),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .quarterTurns(vm.deviceTurn),
            ) {
                vm.overlay?.let { StampPreview(it, vm.overlayFrameWidth, config, onTap = vm::cycleStampPosition) }
            }
        }

        if (cam.level) {
            Box(
                Modifier
                    .fillMaxSize()
                    .upright(),
            ) {
                LevelIndicator(rememberRollDegrees(cam.level))
            }
        }
        // Only once the camera sends data, so it's never an empty box.
        if (cam.histogram && histogram != null) {
            HistogramView(
                histogram,
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = overlayTop),
            )
        }
        GpsStatusPill(
            vm,
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = overlayTop)
                .uprightQuarter(),
        )
        if (vm.isRecording) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = overlayTop + 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecTimer(vm.recSeconds)
                if (cam.allowVideoTagging) {
                    CameraChip("Tag (${vm.taggedMoments.size})", on = true, enabled = true, icon = Icons.Filled.BookmarkAdd, onClick = vm::tagMoment)
                }
            }
        }
        if (vm.isTimelapseActive) {
            CameraPill(
                text = "Time-lapse · frame ${vm.timelapseFrameCount} · every ${cam.timelapseIntervalSec}s",
                dotColor = CameraColors.Recording,
                container = CameraColors.ScrimStrong,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = overlayTop + 40.dp),
            )
        }
        val bind = vm.bind
        if (vm.mode == CaptureMode.VIDEO && bind?.ok == true && bind?.stampOnVideo == false) {
            CameraPill(
                text = "No stamp on video",
                icon = Icons.Filled.Info,
                container = CameraColors.ErrorScrim,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }

        ShutterBlink(vm.shutterFlashes)
        vm.timerCountdown?.let { TimerCountdown(it, onCancel = vm::cancelTimer) }
        // Normal shots only show on the shutter; slower jobs (PiP, scans) say what they're doing.
        if (vm.busy && vm.busyText.isNotBlank()) BusyCard(vm.busyText)
        vm.error?.let { ErrorCard(it) }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraPreview(controller: CameraController, cam: CameraSettings) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller.surfaceProvider = surfaceProvider
                val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val zs = controller.camera?.cameraInfo?.zoomState?.value ?: return true
                        val target = (zs.zoomRatio * detector.scaleFactor).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                        controller.setZoomRatio(target)
                        return true
                    }
                })
                setOnTouchListener { view, event ->
                    scaleDetector.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                        val point = (view as PreviewView).meteringPointFactory.createPoint(event.x, event.y)
                        if (cam.motionTrackingFocus) {
                            controller.startFocusTracking(point)
                            Toast.makeText(ctx, "Motion tracking focus locked", Toast.LENGTH_SHORT).show()
                        } else {
                            controller.focusAt(point)
                        }
                    }
                    true
                }
            }
        },
    )
}

@Composable
private fun FullFrameImage(bmp: Bitmap, description: String, alpha: Float, scale: ContentScale = ContentScale.FillBounds) {
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = description,
        contentScale = scale,
        alpha = alpha,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DocumentGuide() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(28.dp)
            .border(2.dp, CameraColors.DocumentGuide, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.TopCenter,
    ) {
        CameraPill(
            text = "Align document inside the frame",
            icon = Icons.Filled.Description,
            iconTint = CameraColors.DocumentGuide,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The rendered stamp at the size and place it will have on the photo; tapping it moves it
 * bottom → top → center. [frameWidth] is the width of the frame [ov] was drawn for.
 */
@Composable
private fun StampPreview(ov: Bitmap, frameWidth: Int, config: StampConfig, onTap: () -> Unit) {
    if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
        Image(
            bitmap = ov.asImageBitmap(),
            contentDescription = "Stamp preview",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onTap),
        )
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same proportions as PhotoComposer.compose: centred, 2% margin.
            val frac = (ov.width.toFloat() / frameWidth.coerceAtLeast(1)).coerceIn(0.1f, 1f)
            val margin = maxWidth * 0.02f
            Image(
                bitmap = ov.asImageBitmap(),
                contentDescription = "Stamp preview",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .align(
                        when (config.position) {
                            StampPosition.BOTTOM -> Alignment.BottomCenter
                            StampPosition.TOP -> Alignment.TopCenter
                            StampPosition.CENTER -> Alignment.Center
                        }
                    )
                    .padding(vertical = margin)
                    .fillMaxWidth(frac)
                    .clickable(onClick = onTap),
            )
        }
    }
}

/** A quick dark blink over the viewfinder each time a shot is taken. */
@Composable
private fun ShutterBlink(shots: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(shots) {
        if (shots == 0) return@LaunchedEffect
        alpha.snapTo(0.75f)
        alpha.animateTo(0f, tween(durationMillis = 220))
    }
    if (alpha.value > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.Black),
        )
    }
}

@Composable
private fun TimerCountdown(seconds: Int, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CameraColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$seconds",
                modifier = Modifier.upright(),
                color = CameraColors.OnScrim,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 112.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun BoxScope.BusyCard(text: String) {
    Column(
        Modifier
            .align(Alignment.Center)
            .background(CameraColors.ScrimStrong, MaterialTheme.shapes.large)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = CameraColors.OnScrim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BoxScope.ErrorCard(text: String) {
    Text(
        text,
        color = CameraColors.OnScrim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp)
            .background(CameraColors.ErrorScrim, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
