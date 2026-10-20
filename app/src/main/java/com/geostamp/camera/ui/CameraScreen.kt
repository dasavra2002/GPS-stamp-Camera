package com.geostamp.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geostamp.camera.camera.BindResult
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.camera.LensCaps
import com.geostamp.camera.camera.LensInfo
import com.geostamp.camera.camera.ManualState
import com.geostamp.camera.camera.PhotoSpec
import com.geostamp.camera.camera.SensorInfo
import com.geostamp.camera.camera.VideoSpec
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.FrameGuide
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.StampTemplate
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.PhotoSaver
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(
    settings: StampSettings,
    cam: CameraSettings,
    repo: StampDataRepository,
    controller: CameraController,
    shutterTrigger: kotlinx.coroutines.flow.SharedFlow<Unit>? = null,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsState()
    val readout by controller.readout.collectAsState()
    val histogram by controller.histogram.collectAsState()
    val config = settings.snapshot()
    val manual = remember { ManualState() }

    var lenses by remember { mutableStateOf<List<LensInfo>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var bind by remember { mutableStateOf<BindResult?>(null) }
    var caps by remember { mutableStateOf(LensCaps()) }
    var sensor by remember { mutableStateOf(SensorInfo()) }
    var actualRes by remember { mutableStateOf<Size?>(null) }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(1f) }
    var zoomNow by remember { mutableFloatStateOf(1f) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastMime by remember { mutableStateOf("image/jpeg") }
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var openPanel by remember { mutableStateOf<Panel?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recSeconds by remember { mutableLongStateOf(0L) }
    var isTimelapseActive by remember { mutableStateOf(false) }
    var timelapseFrameCount by remember { mutableIntStateOf(0) }
    var timelapseJob by remember { mutableStateOf<Job?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var landscape by remember { mutableStateOf(false) }
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                settings.note = text
                Toast.makeText(context, "Voice Note: $text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startVoiceNote() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak photo note or asset name…")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }
    val isRecording = recording != null

    var timerCountdown by remember { mutableStateOf<Int?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var showGhostOverlay by remember { mutableStateOf(cam.ghostOverlay) }
    var ghostAlpha by remember { mutableFloatStateOf(0.4f) }

    val currentLat = data.latitude
    val currentLon = data.longitude
    val siteLat = settings.siteLat
    val siteLon = settings.siteLon
    val siteRadius = settings.siteRadiusM
    val siteName = settings.siteName

    val geofenceDistanceM: Float? = remember(currentLat, currentLon, siteLat, siteLon) {
        if (currentLat != null && currentLon != null && siteLat != null && siteLon != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(currentLat, currentLon, siteLat, siteLon, results)
            results[0]
        } else null
    }

    // Find every camera on the phone.
    LaunchedEffect(Unit) {
        try {
            lenses = controller.init()
            if (lenses.isEmpty()) error = "No cameras found"
        } catch (e: Exception) {
            error = "Camera error: ${e.message}"
        }
    }

    // Ask for the microphone the first time video mode is opened (videos record silently without it).
    LaunchedEffect(mode) {
        if (mode == CaptureMode.VIDEO && !micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val photoSpec = PhotoSpec(cam.hdrPhoto, cam.photoExtension, cam.photoFormat, cam.photoAspect, cam.photoResolution, cam.histogram)
    val videoSpec = VideoSpec(cam.videoRes, cam.videoAspect, cam.fps, cam.bitrate, cam.videoColor, cam.stabilization, cam.histogram)
    val spec: Any = if (mode == CaptureMode.VIDEO) videoSpec else photoSpec

    // Bind the chosen lens with the chosen mode and settings, and follow its zoom range.
    DisposableEffect(lenses, selected, mode, spec) {
        val lens = lenses.getOrNull(selected)
        var observer: Observer<ZoomState>? = null
        if (lens != null) {
            val r = if (mode == CaptureMode.VIDEO) controller.bindVideo(owner, lens, videoSpec)
            else controller.bindPhoto(owner, lens, photoSpec)
            bind = r
            caps = controller.capabilities(lens)
            sensor = controller.sensorInfo
            error = if (r.ok) null else (r.notice ?: "Couldn't open ${lens.shortLabel}")
            if (r.ok && r.notice != null) Toast.makeText(context, r.notice, Toast.LENGTH_LONG).show()
            controller.applyManual(manual, if (mode == CaptureMode.VIDEO) cam.fps else 30)
            controller.camera?.let { c ->
                val o = Observer<ZoomState> { z ->
                    zoomMin = z.minZoomRatio
                    zoomMax = z.maxZoomRatio
                    zoomNow = z.zoomRatio
                }
                c.cameraInfo.zoomState.observe(owner, o)
                observer = o
            }
        }
        val cam0 = controller.camera
        onDispose { observer?.let { o -> cam0?.cameraInfo?.zoomState?.removeObserver(o) } }
    }

    // The sound-level meter and the video recorder can't share the microphone reliably,
    // so the meter pauses while recording.
    LaunchedEffect(isRecording) {
        repo.setSoundEnabled(!isRecording && settings.isEnabled(StampField.SOUND_LEVEL))
    }

    fun warnIfNoGps() {
        if (repo.data.value.latitude == null) {
            Toast.makeText(context, "No GPS fix yet – location fields will be blank", Toast.LENGTH_SHORT).show()
        }
    }

    // Resolution actually picked by the camera (shown in the info bar).
    LaunchedEffect(bind) {
        delay(800)
        actualRes = controller.currentResolution()
    }

    // Push manual ISO / shutter / WB / focus / EV whenever they change.
    LaunchedEffect(manual.iso, manual.shutterNs, manual.wb, manual.kelvin, manual.tint, manual.focusDiopters, manual.ev, bind) {
        controller.applyManual(manual, if (mode == CaptureMode.VIDEO) cam.fps else 30)
    }

    // Release the camera (and stop any recording) when leaving this screen.
    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
            controller.unbind()
        }
    }

    // Keep photos/videos upright when the phone is turned sideways.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rot = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                controller.targetRotation = rot
                landscape = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // In video mode the stamp's clock ticks every second (and is burned into each frame).
    LaunchedEffect(mode) {
        while (mode == CaptureMode.VIDEO) {
            delay(1000)
            tick++
        }
    }

    val nativeRatio = bind?.nativeRatio ?: (4f / 3f)
    val photoCrop = mode != CaptureMode.VIDEO && cam.photoAspect.cropped && bind?.format != PhotoFormat.RAW
    val frameLongShort = if (photoCrop) cam.photoAspect.ratio else nativeRatio

    // Live stamp preview, drawn by the same renderer used for saving.
    LaunchedEffect(data, config, tick, mode, frameLongShort) {
        val lensLabel = lenses.getOrNull(selected)?.label
        val bmp = withContext(Dispatchers.Default) {
            WatermarkRenderer.render(
                PhotoComposer.stampWidthFor(1080, (1080 * frameLongShort).toInt(), config.size),
                config, data, ZonedDateTime.now(), lensLabel,
                // In video mode this same bitmap is burned into the recording, so no placeholder hints.
                preview = mode != CaptureMode.VIDEO,
            )
        }
        overlay = bmp
        controller.setVideoStamp(if (mode == CaptureMode.VIDEO) bmp else null, config.size, config.position == StampPosition.TOP)
    }

    fun stampDataFor(lens: LensInfo) = repo.data.value.let { d0 ->
        if (lens.isFront) d0.copy(azimuthDeg = d0.azimuthDeg?.let { (it + 180f) % 360f }) else d0
    }

    fun uploadLastToGoogleDrive(targetUri: Uri? = null) {
        val uri = targetUri ?: lastUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = lastMime
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = lastMime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Upload to Google Drive / Cloud"))
        }
    }

    suspend fun saveStamped(bmp0: Bitmap, lens: LensInfo) {
        val time = ZonedDateTime.now()
        val cfg = settings.snapshot()
        val d = stampDataFor(lens)
        val result = withContext(Dispatchers.Default) {
            val bmp = if (photoCrop) PhotoComposer.cropToAspect(bmp0, cam.photoAspect.ratio) else bmp0
            val stamped = PhotoComposer.compose(bmp, cfg, d, time, lens.label)
            val uri = PhotoSaver.save(context, stamped, d, time, subFolder = cfg.siteName.ifBlank { null })
            val thumbW = 240
            val thumb = Bitmap.createScaledBitmap(stamped, thumbW, (stamped.height * thumbW / stamped.width).coerceAtLeast(1), true)
            stamped.recycle()
            if (bmp !== stamped && !bmp.isRecycled) bmp.recycle()
            if (bmp0 !== bmp && bmp0 !== stamped && !bmp0.isRecycled) bmp0.recycle()
            uri to thumb
        }
        settings.nextNumber = settings.nextNumber + 1
        lastUri = result.first
        lastMime = "image/jpeg"
        lastThumb = result.second
        if (result.first == null) {
            Toast.makeText(context, "Couldn't save photo", Toast.LENGTH_SHORT).show()
        } else if (cam.autoDriveUpload) {
            uploadLastToGoogleDrive()
        }
    }

    suspend fun capturePhoto(lens: LensInfo) {
        val location = repo.lastLocation
        when (bind?.format ?: PhotoFormat.JPEG) {
            PhotoFormat.RAW -> {
                val uri = controller.takeRawOnly(location)
                lastUri = uri
                lastMime = "image/x-adobe-dng"
                lastThumb = null
                Toast.makeText(context, if (uri != null) "RAW (DNG) saved – RAW files can't carry a stamp" else "RAW capture failed", Toast.LENGTH_SHORT).show()
            }
            PhotoFormat.RAW_JPEG -> {
                val tmp = File(context.cacheDir, "pair_${System.nanoTime()}.jpg")
                try {
                    controller.takeRawAndJpeg(tmp, location)
                    val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeFile(context, tmp) }
                    saveStamped(bmp, lens)
                } finally {
                    tmp.delete()
                }
            }
            PhotoFormat.JPEG -> {
                val photo = controller.takePhoto()
                var bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees) }
                if (cam.pipMode && lenses.size > 1) {
                    val oppositeLens = lenses.firstOrNull { it.isFront != lens.isFront }
                    if (oppositeLens != null) {
                        busyText = "Selfie PiP…"
                        val r = controller.bindPhoto(owner, oppositeLens, photoSpec)
                        if (r.ok) {
                            delay(250)
                            try {
                                val secondPhoto = controller.takePhoto()
                                val secondBmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(secondPhoto.jpeg, secondPhoto.rotationDegrees) }
                                val composite = withContext(Dispatchers.Default) { PhotoComposer.compositePip(bmp, secondBmp) }
                                if (secondBmp !== composite && !secondBmp.isRecycled) secondBmp.recycle()
                                if (bmp !== composite && !bmp.isRecycled) bmp.recycle()
                                bmp = composite
                            } catch (_: Exception) {}
                            controller.bindPhoto(owner, lens, photoSpec)
                        }
                    }
                }
                if (cam.documentMode) {
                    val enhanced = withContext(Dispatchers.Default) { PhotoComposer.applyDocumentEnhancement(bmp) }
                    if (enhanced !== bmp && !bmp.isRecycled) bmp.recycle()
                    bmp = enhanced
                }
                saveStamped(bmp, lens)
            }
        }
    }

    suspend fun executeCapture(lens: LensInfo, count: Int) {
        busy = true
        try {
            for (i in 1..count) {
                busyText = if (count > 1) "Burst $i/$count…" else "Saving…"
                capturePhoto(lens)
                if (i < count) delay(300)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            busy = false
        }
    }

    fun shoot() {
        val lens = lenses.getOrNull(selected) ?: return
        if (busy) return
        warnIfNoGps()
        val timer = cam.timerSeconds
        val burst = cam.burstCount.coerceAtLeast(1)
        if (timer > 0) {
            timerJob?.cancel()
            timerJob = scope.launch {
                for (s in timer downTo 1) {
                    timerCountdown = s
                    delay(1000)
                }
                timerCountdown = null
                executeCapture(lens, burst)
            }
        } else {
            scope.launch {
                executeCapture(lens, burst)
            }
        }
    }

    fun scanBarcode() {
        if (busy) return
        busy = true
        busyText = "Scanning Barcode…"
        scope.launch {
            try {
                val photo = controller.takePhoto()
                val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees) }
                val code = withContext(Dispatchers.Default) { com.geostamp.camera.stamp.BarcodeScanner.scan(bmp) }
                bmp.recycle()
                if (!code.isNullOrBlank()) {
                    settings.note = "Asset: $code"
                    Toast.makeText(context, "Scanned: $code", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No QR or Barcode detected in frame", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun shareLastToWhatsApp() {
        val uri = lastUri ?: return
        val text = buildString {
            append("📍 GeoStamp Photo\n")
            data.title?.let { append("🏢 Location: $it\n") }
            if (data.latitude != null && data.longitude != null) {
                append("🗺 GPS: ${data.latitude}, ${data.longitude}\n")
            }
            data.plusCode?.let { append("🌐 Plus Code: $it\n") }
            append("⏰ Time: ${WatermarkRenderer.formatDate(config, ZonedDateTime.now())}\n")
            if (settings.note.isNotBlank()) append("📝 Note: ${settings.note}\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Share via"))
        }
    }

    fun scanSignboardText() {
        if (busy) return
        busy = true
        busyText = "Reading Signboard Text…"
        scope.launch {
            try {
                val photo = controller.takePhoto()
                val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees) }
                val detected = withContext(Dispatchers.Default) { com.geostamp.camera.ai.VisionAnalyzer.recognizeText(bmp) }
                bmp.recycle()
                if (!detected.isNullOrBlank()) {
                    val firstLine = detected.lines().firstOrNull { it.isNotBlank() } ?: detected
                    settings.note = firstLine.take(80)
                    Toast.makeText(context, "Detected text: $firstLine", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No text detected in view", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "OCR error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun autoCaptionScene() {
        if (busy) return
        busy = true
        busyText = "Generating Scene Caption…"
        scope.launch {
            try {
                val photo = controller.takePhoto()
                val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees) }
                val caption = withContext(Dispatchers.Default) { com.geostamp.camera.ai.VisionAnalyzer.autoCaption(bmp) }
                bmp.recycle()
                if (!caption.isNullOrBlank()) {
                    settings.note = caption
                    Toast.makeText(context, "Caption: $caption", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Could not identify scene", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Caption error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun toggleTimelapse() {
        if (isTimelapseActive) {
            timelapseJob?.cancel()
            timelapseJob = null
            isTimelapseActive = false
            Toast.makeText(context, "Timelapse ended. $timelapseFrameCount frames captured.", Toast.LENGTH_LONG).show()
            return
        }
        val lens = lenses.getOrNull(selected) ?: return
        isTimelapseActive = true
        timelapseFrameCount = 0
        val sessionTag = "Timelapse_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val sub = if (settings.siteName.isNotBlank()) "${settings.siteName}/$sessionTag" else sessionTag
        timelapseJob = scope.launch {
            Toast.makeText(context, "Timelapse started (every ${cam.timelapseIntervalSec}s)", Toast.LENGTH_SHORT).show()
            try {
                while (isActive && isTimelapseActive) {
                    val photo = controller.takePhoto()
                    val rawBmp = withContext(Dispatchers.Default) {
                        PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees, lens.isFront)
                    }
                    val time = ZonedDateTime.now()
                    val cfg = settings.snapshot()
                    val d = stampDataFor(lens)
                    val frameUri = withContext(Dispatchers.Default) {
                        val bmp = if (photoCrop) PhotoComposer.cropToAspect(rawBmp, cam.photoAspect.ratio) else rawBmp
                        val stamped = PhotoComposer.compose(bmp, cfg, d, time, lens.label)
                        val uri = PhotoSaver.save(context, stamped, d, time, prefix = "Frame", subFolder = sub)
                        val thumbW = 240
                        val thumb = Bitmap.createScaledBitmap(stamped, thumbW, (stamped.height * thumbW / stamped.width).coerceAtLeast(1), true)
                        stamped.recycle()
                        if (bmp !== stamped && !bmp.isRecycled) bmp.recycle()
                        if (rawBmp !== bmp && rawBmp !== stamped && !rawBmp.isRecycled) rawBmp.recycle()
                        uri to thumb
                    }
                    lastUri = frameUri.first
                    lastMime = "image/jpeg"
                    lastThumb = frameUri.second
                    timelapseFrameCount++
                    settings.nextNumber = settings.nextNumber + 1
                    delay(cam.timelapseIntervalSec * 1000L)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Toast.makeText(context, "Timelapse stopped: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isTimelapseActive = false
            }
        }
    }

    fun toggleAttendanceMode() {
        cam.attendanceMode = !cam.attendanceMode
        if (cam.attendanceMode) {
            val frontIdx = lenses.indexOfFirst { it.isFront }
            if (frontIdx >= 0) selected = frontIdx
            settings.applyTemplate(StampTemplate.ATTENDANCE)
            settings.note = "ATTENDANCE VERIFIED"
            settings.setEnabled(StampField.QR_CODE, true)
            Toast.makeText(context, "Attendance Punch Mode ON", Toast.LENGTH_SHORT).show()
        } else {
            val backIdx = lenses.indexOfFirst { !it.isFront }
            if (backIdx >= 0) selected = backIdx
            settings.applyTemplate(StampTemplate.STANDARD)
            settings.note = "Captured by GeoStamp Camera"
            Toast.makeText(context, "Standard Mode", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(shutterTrigger) {
        shutterTrigger?.collect {
            if (!busy && mode == CaptureMode.PHOTO) {
                shoot()
            }
        }
    }

    fun shootAllLenses() {
        if (busy || lenses.isEmpty()) return
        busy = true
        scope.launch {
            var saved = 0
            try {
                for ((i, lens) in lenses.withIndex()) {
                    busyText = "Lens ${i + 1}/${lenses.size}: ${lens.shortLabel}"
                    val r = controller.bindPhoto(owner, lens, photoSpec)
                    if (!r.ok) continue
                    bind = r
                    controller.applyManual(manual, 30)
                    delay(1300) // let focus and exposure settle
                    try {
                        capturePhoto(lens)
                        saved++
                    } catch (e: Exception) {
                        // skip lenses that can't capture (e.g. depth sensors)
                    }
                }
            } finally {
                lenses.getOrNull(selected)?.let { bind = controller.bindPhoto(owner, it, photoSpec) }
                controller.applyManual(manual, 30)
                busy = false
                Toast.makeText(context, "Saved $saved photo(s) from ${lenses.size} cameras", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleRecording() {
        recording?.let {
            it.stop()
            return
        }
        if (!controller.isVideoReady) return
        warnIfNoGps()
        val r = controller.startRecording(repo.lastLocation, micGranted) { event ->
            when (event) {
                is VideoRecordEvent.Status -> recSeconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recSeconds = 0
                    val uri = event.outputResults.outputUri
                    if (uri != Uri.EMPTY) {
                        settings.nextNumber = settings.nextNumber + 1
                        lastUri = uri
                        lastMime = "video/mp4"
                        scope.launch {
                            lastThumb = withContext(Dispatchers.IO) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    runCatching { context.contentResolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()
                                } else null
                            }
                        }
                        Toast.makeText(context, "Video saved to Movies/GeoStamp", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Recording failed (error ${event.error})", Toast.LENGTH_LONG).show()
                    }
                }
                else -> Unit
            }
        }
        recording = r
        if (r == null) Toast.makeText(context, "Video isn't ready", Toast.LENGTH_SHORT).show()
    }

    val formatText = if (mode == CaptureMode.PHOTO) {
        val f = when (bind?.format) {
            PhotoFormat.RAW_JPEG -> "RAW+J"
            PhotoFormat.RAW -> "RAW"
            else -> "JPG"
        }
        val mp = actualRes?.let { String.format(Locale.US, " %.0fMP", it.width.toLong() * it.height / 1_000_000.0) } ?: ""
        "$f ${cam.photoAspect.label}$mp" + (bind?.hdrLabel?.let { " $it" } ?: "")
    } else {
        val res = when (cam.videoRes) { VideoRes.UHD -> "4K"; VideoRes.FHD -> "1080"; VideoRes.HD -> "720"; VideoRes.SD -> "480" }
        val color = when (bind?.color) { VideoColor.HLG -> " HLG"; VideoColor.HDR10 -> " HDR10"; VideoColor.HDR10_PLUS -> " HDR10+"; VideoColor.DOLBY_VISION -> " DV"; else -> "" }
        "$res ${cam.videoAspect.label}$color"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ---- top bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == CaptureMode.PHOTO) {
                IconButton(onClick = {
                    flash = when (flash) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    controller.flashMode = flash
                }) {
                    Icon(
                        when (flash) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            else -> Icons.Filled.FlashOff
                        },
                        contentDescription = "Flash", tint = Color.White,
                    )
                }
            }
            // Quick HDR / RAW toggles (full choices are under FORMAT)
            val hdrSupported = if (mode == CaptureMode.PHOTO) caps.hdrPhoto else caps.videoColors.size > 1
            val hdrOn = bind?.hdrLabel != null
            QuickChip("HDR", hdrOn, hdrSupported && !isRecording) {
                if (mode == CaptureMode.PHOTO) cam.hdrPhoto = !cam.hdrPhoto
                else cam.videoColor = if (cam.videoColor != VideoColor.SDR) VideoColor.SDR
                else caps.videoColors.firstOrNull { it != VideoColor.SDR } ?: VideoColor.SDR
            }
            if (mode == CaptureMode.PHOTO) {
                QuickChip("RAW", bind?.format != null && bind?.format != PhotoFormat.JPEG, caps.anyRaw) {
                    cam.photoFormat = if (cam.photoFormat == PhotoFormat.JPEG) {
                        if (caps.rawJpeg) PhotoFormat.RAW_JPEG else PhotoFormat.RAW
                    } else PhotoFormat.JPEG
                }
                if (caps.nightExtension) {
                    QuickChip("🌙 Night", cam.photoExtension == ExtensionModeType.NIGHT, !isRecording) {
                        cam.photoExtension = if (cam.photoExtension == ExtensionModeType.NIGHT) ExtensionModeType.NONE else ExtensionModeType.NIGHT
                    }
                }
                if (caps.bokehExtension) {
                    QuickChip("👤 Portrait", cam.photoExtension == ExtensionModeType.BOKEH, !isRecording) {
                        cam.photoExtension = if (cam.photoExtension == ExtensionModeType.BOKEH) ExtensionModeType.NONE else ExtensionModeType.BOKEH
                    }
                }
                QuickChip("Punch In", cam.attendanceMode, !isRecording) {
                    toggleAttendanceMode()
                }
                QuickChip("PiP", cam.pipMode, lenses.size > 1 && !isRecording) {
                    cam.pipMode = !cam.pipMode
                }
                QuickChip("DOC", cam.documentMode, !isRecording) {
                    cam.documentMode = !cam.documentMode
                }
            }
            Text(
                text = if (data.latitude != null) String.format(Locale.US, " GPS ±%.0fm", data.accuracyM ?: 0f) else " GPS…",
                color = if (data.latitude != null) Color(0xFF81C784) else Color(0xFFFFB74D),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            if (mode != CaptureMode.VIDEO) {
                IconButton(onClick = { startVoiceNote() }, enabled = !isRecording && !busy) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice Note Dictation", tint = Accent)
                }
                IconButton(onClick = { scanBarcode() }, enabled = !isRecording && !busy) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Asset QR", tint = Accent)
                }
                IconButton(onClick = { scanSignboardText() }, enabled = !isRecording && !busy) {
                    Icon(Icons.Filled.TextFields, contentDescription = "Read Signboard Text", tint = Accent)
                }
                IconButton(onClick = { autoCaptionScene() }, enabled = !isRecording && !busy) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Scene Caption", tint = Accent)
                }
                if (lastThumb != null) {
                    IconButton(onClick = { showGhostOverlay = !showGhostOverlay }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Ghost Angle Overlay", tint = if (showGhostOverlay) Accent else Color.White)
                    }
                }
            }
            IconButton(onClick = { repo.refreshAll() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh location data", tint = Color.White)
            }
            IconButton(onClick = onOpenGallery, enabled = !isRecording) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Stamp gallery photos", tint = Color.White)
            }
            IconButton(onClick = onOpenSettings, enabled = !isRecording) {
                Icon(Icons.Filled.Settings, contentDescription = "Stamp settings", tint = Color.White)
            }
        }

        // ---- Geofence status strip ----
        if (geofenceDistanceM != null) {
            val outside = geofenceDistanceM > siteRadius
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (outside) Color(0xCCB71C1C) else Color(0x991B5E20))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (outside) "⚠️ Outside ${siteName.ifBlank { "Site" }}: ${geofenceDistanceM.toInt()}m (Limit ${siteRadius.toInt()}m)"
                    else "✅ In Site Geofence (${geofenceDistanceM.toInt()}m)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "PIN HERE",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (currentLat != null && currentLon != null) {
                            settings.siteLat = currentLat
                            settings.siteLon = currentLon
                            Toast.makeText(context, "Site pin set to current location", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        } else if (settings.siteName.isNotBlank() && currentLat != null && currentLon != null && settings.siteLat == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x9937474F))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("📍 Site: ${settings.siteName} (No pin)", color = Color.White, fontSize = 12.sp)
                Text("SET PIN", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                    settings.siteLat = currentLat
                    settings.siteLon = currentLon
                    Toast.makeText(context, "Site pin set to current location", Toast.LENGTH_SHORT).show()
                })
            }
        }

        // ---- Blackmagic-style info bar ----
        InfoBar(
            mode = mode, manual = manual, readout = readout, cam = cam, sensor = sensor,
            formatText = formatText, open = openPanel, enabled = true,
            onOpen = { p ->
                if (isRecording && (p == Panel.FPS || p == Panel.FORMAT)) {
                    Toast.makeText(context, "Stop recording to change this", Toast.LENGTH_SHORT).show()
                } else {
                    openPanel = p
                }
            },
        )

        // ---- preview with overlays ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            BoxWithConstraints(
                Modifier
                    .align(Alignment.Center)
                    .aspectRatio(1f / nativeRatio),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
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
                                    controller.focusAt(point)
                                }
                                true
                            }
                        }
                    },
                )

                if (showGhostOverlay && lastThumb != null && mode == CaptureMode.PHOTO) {
                    Image(
                        bitmap = lastThumb!!.asImageBitmap(),
                        contentDescription = "Ghost alignment overlay",
                        contentScale = ContentScale.Crop,
                        alpha = ghostAlpha,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                GridOverlay(cam.grid)
                if (photoCrop) FrameMask(1f / cam.photoAspect.ratio, solid = true)
                if (cam.frameGuide != FrameGuide.OFF) {
                    val r = cam.frameGuide.ratio
                    FrameMask(if (landscape) 1f / r else r, solid = false)
                }

                if (cam.documentMode) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(28.dp)
                            .border(2.dp, Color(0xFF81C784), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            "📄 ALIGN RECEIPT / DOCUMENT",
                            color = Color(0xFF81C784),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                // Stamp preview, inside the crop frame when a cropped photo format is chosen.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .aspectRatio(1f / frameLongShort),
                ) {
                    overlay?.let { ov ->
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val frac = ov.width / 1080f
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
                                    .clickable {
                                        settings.position = when (settings.position) {
                                            StampPosition.BOTTOM -> StampPosition.TOP
                                            StampPosition.TOP -> StampPosition.CENTER
                                            StampPosition.CENTER -> StampPosition.BOTTOM
                                        }
                                    }
                                    .padding(vertical = margin)
                                    .width(maxWidth * frac),
                            )
                        }
                    }
                }

                if (cam.level) LevelIndicator(rememberRollDegrees(cam.level))
                if (cam.histogram) {
                    HistogramView(histogram, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                if (isRecording) RecTimer(recSeconds, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                if (isTimelapseActive) {
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC000000))
                            .border(1.dp, Accent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        Text(
                            "TIMELAPSE REC · Frame #$timelapseFrameCount (${cam.timelapseIntervalSec}s)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (mode == CaptureMode.VIDEO && bind?.ok == true && bind?.stampOnVideo == false) {
                    Text(
                        "Stamp not burned into video on this phone",
                        color = Color.White, fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0x99B71C1C), RoundedCornerShape(4.dp))
                            .padding(4.dp),
                    )
                }

                if (timerCountdown != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$timerCountdown",
                                color = Accent,
                                fontSize = 96.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    timerJob?.cancel()
                                    timerCountdown = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    }
                }

                if (busy) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(busyText, color = Color.White)
                    }
                }
                error?.let {
                    Text(
                        it, color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xCCB71C1C), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
            }

            // Control panel slides over the bottom of the image, Blackmagic style.
            openPanel?.let { p ->
                val evRange = controller.camera?.cameraInfo?.exposureState?.let {
                    if (it.isExposureCompensationSupported) it.exposureCompensationRange.lower..it.exposureCompensationRange.upper else null
                }
                Box(Modifier.align(Alignment.BottomCenter)) {
                    ControlPanel(
                        panel = p, mode = mode, manual = manual, readout = readout, sensor = sensor, caps = caps, cam = cam,
                        evRange = evRange,
                        photoSizes = if (cam.photoAspect.native16x9) sensor.photoSizes169 else sensor.photoSizes43,
                    )
                }
            }
        }

        // ---- zoom presets ----
        if (zoomMax > 1.01f && openPanel == null) {
            val presets = (listOf(zoomMin) + listOf(1f, 2f, 3f, 5f, 10f))
                .filter { it in zoomMin..zoomMax }
                .distinctBy { (it * 10).toInt() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                presets.forEach { z ->
                    val active = kotlin.math.abs(zoomNow - z) < 0.05f
                    Text(
                        text = if (z < 1f) String.format(Locale.US, "%.1fx", z) else "${z.toInt()}x",
                        color = if (active) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(if (active) Accent else Color(0x55FFFFFF))
                            .clickable { controller.setZoomRatio(z) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // ---- lens chips: every camera the phone exposes ----
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lenses.forEachIndexed { i, lens ->
                val active = i == selected
                Text(
                    lens.shortLabel,
                    color = if (active) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) Accent else Color(0x33FFFFFF))
                        .clickable(enabled = !busy && !isRecording) { selected = i }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        // ---- PHOTO / VIDEO ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CaptureMode.entries.forEach { m ->
                Text(
                    m.name,
                    color = if (mode == m) Accent else Color(0xFFBDBDBD),
                    fontSize = 14.sp,
                    fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy && !isRecording) {
                            mode = m
                            openPanel = null
                        }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }

        // ---- shutter row ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable(enabled = lastUri != null && !isRecording) {
                            lastUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, lastMime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val t = lastThumb
                    if (t != null) {
                        Image(t.asImageBitmap(), "Last capture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (lastUri != null) {
                        Text(if (lastMime.startsWith("video")) "VID" else "RAW", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (lastUri != null && !isRecording && !isTimelapseActive) {
                    IconButton(onClick = { shareLastToWhatsApp() }) {
                        Icon(Icons.Filled.Share, "Share via WhatsApp", tint = Color(0xFF25D366))
                    }
                    IconButton(onClick = { uploadLastToGoogleDrive() }) {
                        Icon(Icons.Filled.CloudUpload, "Upload to Google Drive", tint = Color(0xFF4285F4))
                    }
                }
            }

            if (mode == CaptureMode.PHOTO) {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(BorderStroke(4.dp, Color.White), CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(if (busy) Color.Gray else Color.White)
                        .clickable(enabled = !busy && bind?.ok == true) { shoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cam.attendanceMode) {
                        Text("PUNCH", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else if (mode == CaptureMode.TIMELAPSE) {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(BorderStroke(4.dp, Accent), CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = bind?.ok == true) { toggleTimelapse() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (isTimelapseActive) 28.dp else 58.dp)
                            .clip(if (isTimelapseActive) RoundedCornerShape(6.dp) else CircleShape)
                            .background(Accent),
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(BorderStroke(4.dp, Color.White), CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = bind?.ok == true) { toggleRecording() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (isRecording) 28.dp else 58.dp)
                            .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                            .background(Color(0xFFE53935)),
                    )
                }
            }

            if (mode == CaptureMode.PHOTO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        QuickChip(
                            if (cam.timerSeconds > 0) "⏱${cam.timerSeconds}s" else "⏱Off",
                            cam.timerSeconds > 0,
                            !isRecording && !busy,
                        ) {
                            cam.timerSeconds = when (cam.timerSeconds) {
                                0 -> 3
                                3 -> 5
                                5 -> 10
                                else -> 0
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        QuickChip(
                            "💥${cam.burstCount}x",
                            cam.burstCount > 1,
                            !isRecording && !busy,
                        ) {
                            cam.burstCount = when (cam.burstCount) {
                                1 -> 3
                                3 -> 5
                                else -> 1
                            }
                        }
                    }
                    Column(
                        Modifier
                            .width(52.dp)
                            .clickable(enabled = !busy && lenses.size > 1) { shootAllLenses() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.Collections, contentDescription = "Capture from all cameras", tint = Color.White)
                        Text("All", color = Color.White, fontSize = 11.sp)
                    }
                }
            } else if (mode == CaptureMode.TIMELAPSE) {
                QuickChip(
                    "⏱ ${cam.timelapseIntervalSec}s",
                    true,
                    !isTimelapseActive,
                ) {
                    cam.timelapseIntervalSec = when (cam.timelapseIntervalSec) {
                        1 -> 2
                        2 -> 5
                        5 -> 10
                        10 -> 30
                        else -> 1
                    }
                }
            } else {
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (micGranted) "🎙 ON" else "🎙 OFF", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = when {
            !enabled -> Color(0xFF616161)
            on -> Color.Black
            else -> Color.White
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (on && enabled) Accent else Color.Transparent)
            .border(1.dp, if (enabled) Color.White else Color(0xFF616161), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
