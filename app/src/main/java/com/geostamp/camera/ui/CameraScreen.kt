package com.geostamp.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalConfiguration
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.stamp.FrameWatermarkRenderer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.geostamp.camera.VolumeKeyEvent
import com.geostamp.camera.camera.BindResult
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.camera.LensCaps
import com.geostamp.camera.camera.LensInfo
import com.geostamp.camera.camera.ManualState
import com.geostamp.camera.camera.PhotoSpec
import com.geostamp.camera.camera.SensorInfo
import com.geostamp.camera.camera.VideoSpec
import com.geostamp.camera.camera.WbMode
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.GeofenceManager
import com.geostamp.camera.data.FrameGuide
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.SnapshotMode
import com.geostamp.camera.data.SlowMoSpeed
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.data.StampTemplate
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.PhotoSaver
import com.geostamp.camera.stamp.ShotParams
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
    geofenceManager: GeofenceManager? = null,
    volumeKeyTrigger: kotlinx.coroutines.flow.SharedFlow<VolumeKeyEvent>? = null,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val geoMgr = geofenceManager ?: remember { GeofenceManager(context) }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsState()
    val readout by controller.readout.collectAsState()
    val histogram by controller.histogram.collectAsState()
    val focusPeakingBmp by controller.focusPeakingBitmap.collectAsState()
    val exposureVerificationBmp by controller.exposureVerificationBitmap.collectAsState()
    val config = settings.snapshot()
    val manual = remember { ManualState() }
    val taggedMoments = remember { mutableStateListOf<Long>() }

    var lenses by remember { mutableStateOf<List<LensInfo>>(emptyList()) }
    var selected by remember {
        mutableIntStateOf(if (cam.preservePortraitFocalLength) cam.savedLensIndex else 0)
    }
    var mode by remember {
        mutableStateOf(
            if (cam.preserveCameraMode) {
                runCatching { CaptureMode.valueOf(cam.savedCameraMode) }.getOrDefault(CaptureMode.PHOTO)
            } else CaptureMode.PHOTO
        )
    }
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
    var showProBar by remember { mutableStateOf(false) }
    var showToolsBar by remember { mutableStateOf(false) }
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

    // Auto-switch project tags when entering geofence boundaries
    LaunchedEffect(currentLat, currentLon, geoMgr.autoSwitchEnabled.value) {
        if (currentLat != null && currentLon != null && geoMgr.autoSwitchEnabled.value) {
            val active = geoMgr.findActiveSite(currentLat, currentLon)
            if (active != null) {
                val site = active.first
                if (settings.siteName != site.name) {
                    settings.siteName = site.name
                    if (site.contractor.isNotBlank()) settings.custom1Value = site.contractor
                    if (site.client.isNotBlank()) settings.custom2Value = site.client
                    if (site.note.isNotBlank()) settings.note = site.note
                }
            }
        }
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

    // Preserve settings persistence and restoration
    LaunchedEffect(mode) {
        if (cam.preserveCameraMode) {
            cam.savedCameraMode = mode.name
        }
    }

    LaunchedEffect(selected) {
        if (cam.preservePortraitFocalLength) {
            cam.savedLensIndex = selected
        }
    }

    LaunchedEffect(Unit) {
        if (cam.preserveExposureCompensation) {
            manual.ev = cam.savedEv
        }
        if (cam.preserveParameters) {
            manual.iso = if (cam.savedIso >= 0) cam.savedIso else null
            manual.shutterNs = if (cam.savedShutterNs > 0) cam.savedShutterNs else null
            manual.wb = runCatching { WbMode.valueOf(cam.savedWb) }.getOrDefault(WbMode.AUTO)
            manual.kelvin = cam.savedKelvin
            manual.focusDiopters = if (cam.savedFocusDiopters >= 0f) cam.savedFocusDiopters else null
        }
    }

    LaunchedEffect(manual.ev) {
        if (cam.preserveExposureCompensation) {
            cam.savedEv = manual.ev
        }
    }

    LaunchedEffect(manual.iso, manual.shutterNs, manual.wb, manual.kelvin, manual.focusDiopters) {
        if (cam.preserveParameters) {
            cam.savedIso = manual.iso ?: -1
            cam.savedShutterNs = manual.shutterNs ?: -1L
            cam.savedWb = manual.wb.name
            cam.savedKelvin = manual.kelvin
            cam.savedFocusDiopters = manual.focusDiopters ?: -1f
        }
    }

    // Ask for the microphone the first time video mode is opened (videos record silently without it).
    LaunchedEffect(mode) {
        if (mode == CaptureMode.VIDEO && !micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val photoSpec = PhotoSpec(
        hdr = cam.hdrPhoto,
        extension = cam.photoExtension,
        format = cam.photoFormat,
        aspect = cam.photoAspect,
        resolution = cam.photoResolution,
        histogram = cam.histogram,
        focusPeaking = cam.focusPeaking,
        exposureVerification = cam.exposureVerification,
    )
    val videoSpec = VideoSpec(
        res = cam.videoRes,
        aspect = cam.videoAspect,
        fps = cam.fps,
        bitrate = cam.bitrate,
        color = cam.videoColor,
        codec = cam.videoCodec,
        autoFrameRate = cam.autoFrameRate,
        stabilization = cam.stabilization,
        histogram = cam.histogram,
        focusPeaking = cam.focusPeaking,
        exposureVerification = cam.exposureVerification,
    )
    val effectiveVideoSpec = if (mode == CaptureMode.SLOW_MO) videoSpec.copy(fps = cam.slowMoSpeed.targetFps) else videoSpec
    val spec: Any = if (mode.isVideo) effectiveVideoSpec else photoSpec

    // Bind the chosen lens with the chosen mode and settings, and follow its zoom range.
    DisposableEffect(lenses, selected, mode, spec) {
        val lens = lenses.getOrNull(selected)
        var observer: Observer<ZoomState>? = null
        if (lens != null) {
            val r = if (mode.isVideo) controller.bindVideo(owner, lens, effectiveVideoSpec, targetMode = mode)
            else controller.bindPhoto(owner, lens, photoSpec)
            bind = r
            caps = controller.capabilities(lens)
            sensor = controller.sensorInfo
            error = if (r.ok) null else (r.notice ?: "Couldn't open ${lens.shortLabel}")
            if (r.ok && r.notice != null) Toast.makeText(context, r.notice, Toast.LENGTH_LONG).show()
            controller.applyManual(manual, if (mode.isVideo) effectiveVideoSpec.fps else 30)
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
        controller.applyManual(manual, if (mode.isVideo) effectiveVideoSpec.fps else 30)
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
        while (mode.isVideo) {
            delay(1000)
            tick++
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val nativeRatio = bind?.nativeRatio ?: (4f / 3f)
    val photoCrop = !mode.isVideo && cam.photoAspect.cropped && bind?.format != PhotoFormat.RAW
    val frameLongShort = if (photoCrop) cam.photoAspect.ratio else nativeRatio
    val activeNativeRatio = if (isLandscape) nativeRatio else (1f / nativeRatio)
    val activeFrameRatio = if (isLandscape) frameLongShort else (1f / frameLongShort)

    // Live stamp preview, drawn by the same renderer used for saving.
    LaunchedEffect(data, config, tick, mode, frameLongShort, selected, isLandscape) {
        val lens = lenses.getOrNull(selected)
        val lensLabel = lens?.label
        val bmp = withContext(Dispatchers.Default) {
            val w = if (isLandscape) (1080 * frameLongShort).toInt().coerceAtLeast(600) else 1080
            val h = if (isLandscape) 1080 else (1080 * frameLongShort).toInt().coerceAtLeast(600)
            if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                val shotParams = ShotParams(
                    focalMm = lens?.equivFocalMm ?: 26,
                    aperture = lens?.aperture ?: 1.8f,
                    shutterNs = manual.shutterNs ?: readout.exposureNs ?: 33333333L,
                    iso = manual.iso ?: readout.iso ?: 100,
                    model = config.deviceBrand,
                )
                FrameWatermarkRenderer.renderPreview(w, h, config, data, ZonedDateTime.now(), shotParams, lensLabel)
            } else {
                WatermarkRenderer.render(
                    PhotoComposer.stampWidthFor(w, h, config.size),
                    config, data, ZonedDateTime.now(), lensLabel,
                    // In video mode this same bitmap is burned into the recording, so no placeholder hints.
                    preview = !mode.isVideo,
                )
            }
        }
        overlay = bmp
        controller.setVideoStamp(if (mode.isVideo) bmp else null, config.size, config.position == StampPosition.TOP)
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
        val shotParams = ShotParams(
            focalMm = lens.equivFocalMm,
            aperture = lens.aperture,
            shutterNs = readout.exposureNs,
            iso = readout.iso,
            model = cfg.deviceBrand.ifBlank { null },
        )
        val result = withContext(Dispatchers.Default) {
            val bmp = if (photoCrop) PhotoComposer.cropToAspect(bmp0, cam.photoAspect.ratio) else bmp0
            val stamped = PhotoComposer.compose(bmp, cfg, d, time, lens.label, shotParams)
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
                    val shotParams = ShotParams(
                        focalMm = lens.equivFocalMm,
                        aperture = lens.aperture,
                        shutterNs = readout.exposureNs,
                        iso = readout.iso,
                        model = cfg.deviceBrand.ifBlank { null },
                    )
                    val frameUri = withContext(Dispatchers.Default) {
                        val bmp = if (photoCrop) PhotoComposer.cropToAspect(rawBmp, cam.photoAspect.ratio) else rawBmp
                        val stamped = PhotoComposer.compose(bmp, cfg, d, time, lens.label, shotParams)
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

    fun flipCamera() {
        if (busy || isRecording || lenses.size <= 1) return
        val currentLens = lenses.getOrNull(selected)
        val targetIdx = if (currentLens?.isFront == true) {
            lenses.indexOfFirst { !it.isFront }
        } else {
            lenses.indexOfFirst { it.isFront }
        }
        selected = if (targetIdx >= 0) targetIdx else (selected + 1) % lenses.size
    }

    fun takeVideoSnapshot() {
        if (busy) return
        scope.launch {
            val lens = lenses.getOrNull(selected) ?: return@launch
            busy = true
            busyText = if (cam.snapshotMode == SnapshotMode.LIVE_MOMENTS) "Live Moments…" else "Snapshot…"
            try {
                val count = if (cam.snapshotMode == SnapshotMode.LIVE_MOMENTS) 3 else 1
                for (i in 1..count) {
                    val photo = controller.takePhoto()
                    val bmp = withContext(Dispatchers.Default) {
                        PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees)
                    }
                    saveStamped(bmp, lens)
                    if (i < count) delay(200)
                }
                Toast.makeText(context, "Snapshot saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Snapshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
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
                        if (taggedMoments.isNotEmpty()) {
                            val tagsList = taggedMoments.toList()
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    val tagJson = org.json.JSONArray().apply {
                                        tagsList.forEach { put(it) }
                                    }.toString()
                                    val tagFile = File(context.getExternalFilesDir(null), "GeoStamp_Video_${System.currentTimeMillis()}.tags.json")
                                    tagFile.writeText(tagJson)
                                }
                            }
                            taggedMoments.clear()
                        }
                        Toast.makeText(context, "Video saved to Movies/GeoStamp", Toast.LENGTH_SHORT).show()
                    } else {
                        taggedMoments.clear()
                        Toast.makeText(context, "Recording failed (error ${event.error})", Toast.LENGTH_LONG).show()
                    }
                }
                else -> Unit
            }
        }
        recording = r
        if (r == null) Toast.makeText(context, "Video isn't ready", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(volumeKeyTrigger) {
        volumeKeyTrigger?.collect { event ->
            if (busy) return@collect
            when (cam.volumeButtonAction) {
                VolumeButtonAction.SHUTTER -> {
                    when (mode) {
                        CaptureMode.PHOTO -> shoot()
                        CaptureMode.VIDEO, CaptureMode.SLOW_MO -> toggleRecording()
                        CaptureMode.TIMELAPSE -> toggleTimelapse()
                    }
                }
                VolumeButtonAction.TIMER_2S -> {
                    if (mode == CaptureMode.PHOTO) {
                        timerJob?.cancel()
                        timerJob = scope.launch {
                            for (s in 2 downTo 1) {
                                timerCountdown = s
                                delay(1000)
                            }
                            timerCountdown = null
                            val lens = lenses.getOrNull(selected) ?: return@launch
                            executeCapture(lens, cam.burstCount.coerceAtLeast(1))
                        }
                    } else if (mode.isVideo) {
                        toggleRecording()
                    }
                }
                VolumeButtonAction.ZOOM -> {
                    val zs = controller.camera?.cameraInfo?.zoomState?.value ?: return@collect
                    val step = 0.25f
                    val nextZoom = if (event == VolumeKeyEvent.UP) {
                        (zs.zoomRatio + step).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                    } else {
                        (zs.zoomRatio - step).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                    }
                    controller.setZoomRatio(nextZoom)
                }
                VolumeButtonAction.VOLUME -> {
                    // Passed through to system in MainActivity
                }
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

    val formatText = if (mode == CaptureMode.SLOW_MO) {
        val res = when (cam.videoRes) { VideoRes.UHD -> "4K"; VideoRes.FHD -> "1080"; VideoRes.HD -> "720"; VideoRes.SD -> "480" }
        "SLOW-MO ${cam.slowMoSpeed.label} $res ${cam.videoAspect.label}"
    } else if (mode == CaptureMode.PHOTO) {
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

    Row(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Main camera area (Top bar, Info bar, Viewfinder, and portrait controls if portrait)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            // ---- Top Bar (Material 3 Header) ----
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.82f),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Tier 1: Fixed primary action bar with guaranteed settings visibility
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left controls: Flash toggle & Grid Guide toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                        contentDescription = "Flash",
                                        tint = if (flash != ImageCapture.FLASH_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                cam.grid = when (cam.grid) {
                                    GridType.OFF -> GridType.THIRDS
                                    GridType.THIRDS -> GridType.CROSSHAIR
                                    GridType.CROSSHAIR -> GridType.BOTH
                                    GridType.BOTH -> GridType.OFF
                                }
                            }) {
                                Icon(
                                    if (cam.grid != GridType.OFF) Icons.Filled.GridOn else Icons.Filled.GridOff,
                                    contentDescription = "Grid Guides",
                                    tint = if (cam.grid != GridType.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }

                        // Center: GPS Status Pill (tap to refresh)
                        Surface(
                            shape = CircleShape,
                            color = if (data.latitude != null) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, if (data.latitude != null) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color.Transparent),
                            modifier = Modifier.clickable {
                                repo.refreshAll()
                                Toast.makeText(context, "GPS location updated", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (data.latitude != null) Color(0xFF4CAF50) else Color(0xFFFFB300))
                                )
                                Text(
                                    text = if (data.latitude != null) String.format(Locale.US, "GPS ±%.0fm", data.accuracyM ?: 0f) else "GPS Searching…",
                                    color = if (data.latitude != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }

                        // Right controls: PRO toggle, TOOLS toggle, and ALWAYS VISIBLE Settings Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Pro manual toggle pill
                            Surface(
                                shape = CircleShape,
                                color = if (showProBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, if (showProBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    showProBar = !showProBar
                                    if (!showProBar) openPanel = null
                                },
                            ) {
                                Text(
                                    "PRO",
                                    color = if (showProBar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }

                            // Quick tools toggle button
                            IconButton(
                                onClick = { showToolsBar = !showToolsBar },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Quick Tools",
                                    tint = if (showToolsBar) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }

                            // Prominent, guaranteed Material 3 Settings Button
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(38.dp),
                            ) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    enabled = !isRecording,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Tier 2: Horizontally scrollable quick-controls strip (shown when showToolsBar is ON)
                    if (showToolsBar) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
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
                            if (!mode.isVideo) {
                                QuickChip("🎙 Voice", false, !isRecording && !busy) {
                                    startVoiceNote()
                                }
                                QuickChip("🔲 QR", false, !isRecording && !busy) {
                                    scanBarcode()
                                }
                                QuickChip("📄 OCR", false, !isRecording && !busy) {
                                    scanSignboardText()
                                }
                                QuickChip("✨ AI Caption", false, !isRecording && !busy) {
                                    autoCaptionScene()
                                }
                                if (lastThumb != null) {
                                    QuickChip("👻 Ghost", showGhostOverlay, true) {
                                        showGhostOverlay = !showGhostOverlay
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Geofence status strip ----
            val activeSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
                geoMgr.findActiveSite(currentLat, currentLon)
            }
            val nearestSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
                geoMgr.findNearestSite(currentLat, currentLon)
            }

            if (activeSitePair != null) {
                val site = activeSitePair.first
                val dist = activeSitePair.second
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xD91B5E20))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "✅ In Geofence: ${site.name} (${dist.toInt()}m)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "AUTO-TAGGED",
                        color = Color(0xFFA5D6A7),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (nearestSitePair != null) {
                val site = nearestSitePair.first
                val dist = nearestSitePair.second
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "⚠️ Outside ${site.name}: ${dist.toInt()}m (Limit ${site.radiusMeters.toInt()}m)",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else if (geofenceDistanceM != null) {
                val outside = geofenceDistanceM > siteRadius
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (outside) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f) else Color(0x991B5E20))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (outside) "⚠️ Outside ${siteName.ifBlank { "Site" }}: ${geofenceDistanceM.toInt()}m (Limit ${siteRadius.toInt()}m)"
                        else "✅ In Site Geofence (${geofenceDistanceM.toInt()}m)",
                        color = if (outside) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "PIN HERE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("📍 Site: ${settings.siteName} (No pin)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                    Text("SET PIN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        settings.siteLat = currentLat
                        settings.siteLon = currentLon
                        Toast.makeText(context, "Site pin set to current location", Toast.LENGTH_SHORT).show()
                    })
                }
            }

            // ---- Pro Manual Camera Info Bar ----
            if (showProBar) {
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
            }

            // ---- preview with overlays ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                BoxWithConstraints(
                    Modifier
                        .align(Alignment.Center)
                        .aspectRatio(activeNativeRatio),
                ) {
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
                                            Toast.makeText(context, "Motion tracking focus locked", Toast.LENGTH_SHORT).show()
                                        } else {
                                            controller.focusAt(point)
                                        }
                                    }
                                    true
                                }
                            }
                        },
                    )

                    if (cam.focusPeaking && focusPeakingBmp != null) {
                        Image(
                            bitmap = focusPeakingBmp!!.asImageBitmap(),
                            contentDescription = "Focus Peaking",
                            contentScale = ContentScale.FillBounds,
                            alpha = 0.9f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (cam.exposureVerification && exposureVerificationBmp != null) {
                        Image(
                            bitmap = exposureVerificationBmp!!.asImageBitmap(),
                            contentDescription = "Exposure Verification",
                            contentScale = ContentScale.FillBounds,
                            alpha = 0.75f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

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
                    if (photoCrop) FrameMask(if (isLandscape) cam.photoAspect.ratio else 1f / cam.photoAspect.ratio, solid = true)
                    if (cam.frameGuide != FrameGuide.OFF) {
                        val r = cam.frameGuide.ratio
                        FrameMask(if (isLandscape || landscape) r else 1f / r, solid = false)
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
                            .aspectRatio(activeFrameRatio),
                    ) {
                        overlay?.let { ov ->
                            if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                                Image(
                                    bitmap = ov.asImageBitmap(),
                                    contentDescription = "Stamp preview",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            settings.position = when (settings.position) {
                                                StampPosition.BOTTOM -> StampPosition.TOP
                                                StampPosition.TOP -> StampPosition.CENTER
                                                StampPosition.CENTER -> StampPosition.BOTTOM
                                            }
                                        },
                                )
                            } else {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val frac = (ov.width / 1080f).coerceIn(0.65f, 0.98f)
                                    val margin = maxHeight * 0.02f
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
                                            .padding(horizontal = 8.dp, vertical = margin)
                                            .fillMaxWidth(frac),
                                    )
                                }
                            }
                        }
                    }

                    if (cam.level) LevelIndicator(rememberRollDegrees(cam.level))
                    if (cam.histogram) {
                        HistogramView(histogram, Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    if (isRecording) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RecTimer(recSeconds)
                            if (cam.allowVideoTagging) {
                                QuickChip("🏷 Tag (${taggedMoments.size})", on = true, enabled = true) {
                                    taggedMoments.add(recSeconds)
                                    Toast.makeText(context, "Tag marked at ${recSeconds}s", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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

            // ---- Portrait Bottom Controls ----
            if (!isLandscape) {
                // Floating lens switcher or zoom presets capsule (unified)
                if (lenses.size > 1) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .align(Alignment.CenterHorizontally),
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            lenses.forEachIndexed { i, lens ->
                                val active = i == selected
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.clickable(enabled = !busy && !isRecording) { selected = i },
                                ) {
                                    Text(
                                        text = lens.shortLabel,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                } else if (zoomMax > 1.01f && openPanel == null) {
                    val presets = (listOf(zoomMin) + listOf(1f, 2f, 3f, 5f, 10f))
                        .filter { it in zoomMin..zoomMax }
                        .distinctBy { (it * 10).toInt() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        presets.forEach { z ->
                            val active = kotlin.math.abs(zoomNow - z) < 0.05f
                            Surface(
                                shape = CircleShape,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { controller.setZoomRatio(z) },
                            ) {
                                Text(
                                    text = if (z < 1f) String.format(Locale.US, "%.1fx", z) else "${z.toInt()}x",
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                // Capture mode selector (Material 3 segmented pill)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CaptureMode.entries.forEach { m ->
                                val active = mode == m
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .clickable(enabled = !busy && !isRecording) {
                                            mode = m
                                            openPanel = null
                                        }
                                        .padding(horizontal = 2.dp),
                                ) {
                                    Text(
                                        text = m.name,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Shutter and Action Row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left: Thumbnail preview & quick share
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
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
                            IconButton(onClick = { shareLastToWhatsApp() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Share, "Share via WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { uploadLastToGoogleDrive() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.CloudUpload, "Upload to Google Drive", tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Center: Dual-Ring Shutter Button + Snapshot button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShutterButton(
                            mode = mode,
                            busy = busy,
                            isRecording = isRecording,
                            isTimelapseActive = isTimelapseActive,
                            isReady = bind?.ok == true,
                            attendanceMode = cam.attendanceMode,
                            onPhotoClick = { shoot() },
                            onVideoClick = { toggleRecording() },
                            onTimelapseClick = { toggleTimelapse() },
                        )
                        if (isRecording) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x55FFFFFF),
                                border = BorderStroke(2.dp, Color.White),
                                modifier = Modifier.size(48.dp),
                            ) {
                                IconButton(onClick = { takeVideoSnapshot() }, enabled = !busy, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = "Take Snapshot", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    // Right: Camera Switch (Flip) & Timer/Burst chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (mode == CaptureMode.PHOTO) {
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
                        } else if (mode == CaptureMode.TIMELAPSE) {
                            QuickChip("⏱ ${cam.timelapseIntervalSec}s", true, !isTimelapseActive) {
                                cam.timelapseIntervalSec = when (cam.timelapseIntervalSec) {
                                    1 -> 2
                                    2 -> 5
                                    5 -> 10
                                    10 -> 30
                                    else -> 1
                                }
                            }
                        } else if (mode == CaptureMode.SLOW_MO) {
                            QuickChip("⚡ ${cam.slowMoSpeed.label}", true, !isRecording) {
                                cam.slowMoSpeed = when (cam.slowMoSpeed) {
                                    SlowMoSpeed.X2 -> SlowMoSpeed.X4
                                    SlowMoSpeed.X4 -> SlowMoSpeed.X8
                                    SlowMoSpeed.X8 -> SlowMoSpeed.X2
                                }
                            }
                        }

                        // Dedicated Flip Camera Button
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.size(52.dp),
                        ) {
                            IconButton(
                                onClick = { flipCamera() },
                                enabled = !busy && !isRecording && lenses.size > 1,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    Icons.Filled.Cameraswitch,
                                    contentDescription = "Flip Camera",
                                    tint = if (lenses.size > 1) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Landscape Right Deck (Ergonomic thumb shooting deck) ----
        if (isLandscape) {
            Column(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top: Compact mode selector
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CaptureMode.entries.forEach { m ->
                        val active = mode == m
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .clickable(enabled = !busy && !isRecording) {
                                    mode = m
                                    openPanel = null
                                }
                                .padding(vertical = 1.dp),
                        ) {
                            Text(
                                text = m.name,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Center: Lens switcher capsule + Shutter button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (lenses.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                lenses.forEachIndexed { i, lens ->
                                    val active = i == selected
                                    Surface(
                                        shape = CircleShape,
                                        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier.clickable(enabled = !busy && !isRecording) { selected = i },
                                    ) {
                                        Text(
                                            text = lens.shortLabel,
                                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ShutterButton(
                        mode = mode,
                        busy = busy,
                        isRecording = isRecording,
                        isTimelapseActive = isTimelapseActive,
                        isReady = bind?.ok == true,
                        attendanceMode = cam.attendanceMode,
                        onPhotoClick = { shoot() },
                        onVideoClick = { toggleRecording() },
                        onTimelapseClick = { toggleTimelapse() },
                    )

                    if (isRecording) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x55FFFFFF),
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier.size(44.dp),
                        ) {
                            IconButton(onClick = { takeVideoSnapshot() }, enabled = !busy, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Take Snapshot", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Bottom: Thumbnail & Flip Camera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
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
                            Text(if (lastMime.startsWith("video")) "VID" else "RAW", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.size(46.dp),
                    ) {
                        IconButton(
                            onClick = { flipCamera() },
                            enabled = !busy && !isRecording && lenses.size > 1,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = "Flip Camera",
                                tint = if (lenses.size > 1) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    mode: CaptureMode,
    busy: Boolean,
    isRecording: Boolean,
    isTimelapseActive: Boolean,
    isReady: Boolean,
    attendanceMode: Boolean,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onTimelapseClick: () -> Unit,
) {
    val outerRingColor = when (mode) {
        CaptureMode.PHOTO -> Color.White
        CaptureMode.VIDEO -> Color(0xFFE53935)
        CaptureMode.SLOW_MO -> Color(0xFF00E5FF)
        CaptureMode.TIMELAPSE -> Accent
    }

    when (mode) {
        CaptureMode.PHOTO -> {
            Box(
                Modifier
                    .size(78.dp)
                    .border(BorderStroke(4.dp, outerRingColor), CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .background(if (busy) Color.Gray else Color.White)
                    .clickable(enabled = !busy && isReady, onClick = onPhotoClick),
                contentAlignment = Alignment.Center,
            ) {
                if (attendanceMode) {
                    Text("PUNCH", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        CaptureMode.TIMELAPSE -> {
            Box(
                Modifier
                    .size(78.dp)
                    .border(BorderStroke(4.dp, outerRingColor), CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .clickable(enabled = isReady, onClick = onTimelapseClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isTimelapseActive) 28.dp else 56.dp)
                        .clip(if (isTimelapseActive) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Accent),
                )
            }
        }
        CaptureMode.SLOW_MO -> {
            Box(
                Modifier
                    .size(78.dp)
                    .border(BorderStroke(4.dp, outerRingColor), CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .clickable(enabled = isReady, onClick = onVideoClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isRecording) 28.dp else 56.dp)
                        .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Color(0xFF00E5FF)),
                )
            }
        }
        CaptureMode.VIDEO -> {
            Box(
                Modifier
                    .size(78.dp)
                    .border(BorderStroke(4.dp, outerRingColor), CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .clickable(enabled = isReady, onClick = onVideoClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isRecording) 28.dp else 56.dp)
                        .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Color(0xFFE53935)),
                )
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f)
            on -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
        },
        border = BorderStroke(
            1.dp,
            if (on && enabled) MaterialTheme.colorScheme.primary
            else if (enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                on -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
