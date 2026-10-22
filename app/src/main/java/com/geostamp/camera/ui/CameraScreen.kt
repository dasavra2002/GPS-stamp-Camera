package com.geostamp.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.geostamp.camera.ui.theme.CameraColors
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.ExtensionModeType
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.data.StampTemplate
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.location.FixIssue
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.ShotParams
import com.geostamp.camera.stamp.StampedCapture
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
    // Ticks once a second so the GPS pill can show a fix getting old.
    val nowMs by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val fixAgeMs = data.fixElapsedMs?.let { nowMs - it }
    val fixIssues = FixCheck.issues(data.latitude != null, fixAgeMs, data.accuracyM, data.isMock)
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
        val d = repo.data.value
        val age = d.fixElapsedMs?.let { SystemClock.elapsedRealtime() - it }
        val issues = FixCheck.issues(d.latitude != null, age, d.accuracyM, d.isMock)
        FixCheck.warning(issues, age, d.accuracyM)?.let {
            Toast.makeText(context, it, if (FixIssue.NO_FIX in issues) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
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
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > configuration.screenHeightDp
    val nativeRatio = bind?.nativeRatio ?: (4f / 3f)
    val photoCrop = !mode.isVideo && cam.photoAspect.cropped && bind?.format != PhotoFormat.RAW
    val frameLongShort = if (photoCrop) cam.photoAspect.ratio else nativeRatio
    // Height of the floating top bar in landscape, so overlays below it line up with its edge.
    val landscapeTopInset = if (showToolsBar) 104.dp else 60.dp
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

    fun shotParamsFor(lens: LensInfo, cfg: StampConfig) = ShotParams(
        focalMm = lens.equivFocalMm,
        aperture = lens.aperture,
        shutterNs = readout.exposureNs,
        iso = readout.iso,
        model = cfg.deviceBrand.ifBlank { null },
    )

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

    /**
     * [cameraJpeg] is the camera's own JPEG: its EXIF is carried over, and it's kept as the original if asked.
     * [cameraRotation] is CameraX's rotation for an in-memory JPEG, null when the file's EXIF already has it.
     */
    suspend fun saveStamped(bmp0: Bitmap, lens: LensInfo, cameraJpeg: ByteArray? = null, cameraRotation: Int? = null) {
        val cfg = settings.snapshot()
        val request = StampedCapture.Request(
            frame = bmp0,
            config = cfg,
            data = stampDataFor(lens),
            time = ZonedDateTime.now(),
            lensLabel = lens.label,
            shotParams = shotParamsFor(lens, cfg),
            cropRatio = if (photoCrop) cam.photoAspect.ratio else null,
            subFolder = cfg.siteName.ifBlank { null },
            cameraJpeg = cameraJpeg,
            cameraRotation = cameraRotation,
            keepOriginal = cam.keepOriginal,
        )
        val result = withContext(Dispatchers.Default) { StampedCapture.save(context, request) }
        settings.nextNumber = settings.nextNumber + 1
        lastUri = result.uri
        lastMime = "image/jpeg"
        lastThumb = result.thumbnail
        if (result.uri == null) {
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
                    saveStamped(bmp, lens, withContext(Dispatchers.IO) { tmp.readBytes() })
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
                saveStamped(bmp, lens, photo.jpeg, photo.rotationDegrees)
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
                    val cfg = settings.snapshot()
                    val request = StampedCapture.Request(
                        frame = rawBmp,
                        config = cfg,
                        data = stampDataFor(lens),
                        time = ZonedDateTime.now(),
                        lensLabel = lens.label,
                        shotParams = shotParamsFor(lens, cfg),
                        cropRatio = if (photoCrop) cam.photoAspect.ratio else null,
                        prefix = "Frame",
                        subFolder = sub,
                        cameraJpeg = photo.jpeg,
                    )
                    val frame = withContext(Dispatchers.Default) { StampedCapture.save(context, request) }
                    lastUri = frame.uri
                    lastMime = "image/jpeg"
                    lastThumb = frame.thumbnail
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

    @Composable
    fun TopBarContent(isLandscape: Boolean) {
        Column(Modifier.fillMaxWidth()) {
            // Tier 1: flash · GPS status · pro, tools, settings
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mode == CaptureMode.PHOTO) {
                    CameraIconButton(
                        icon = when (flash) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            else -> Icons.Filled.FlashOff
                        },
                        contentDescription = "Flash",
                        active = flash != ImageCapture.FLASH_MODE_OFF,
                        onClick = {
                            flash = when (flash) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                            controller.flashMode = flash
                        },
                    )
                }
                // GPS status, weighted so it never crowds out the action buttons
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                        onClick = {
                            repo.refreshAll()
                            Toast.makeText(context, "Refreshing location", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                CameraChip(
                    text = "PRO",
                    on = showProBar,
                    enabled = true,
                    onClick = {
                        showProBar = !showProBar
                        if (!showProBar) openPanel = null
                    },
                )
                CameraIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Quick tools",
                    active = showToolsBar,
                    onClick = { showToolsBar = !showToolsBar },
                )
                CameraIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    enabled = !isRecording,
                    onClick = onOpenSettings,
                )
            }

            // Tier 2: horizontally scrollable quick tools (shown when showToolsBar is on)
            if (showToolsBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CameraChip(
                        text = if (cam.grid == GridType.OFF) "Grid" else cam.grid.label,
                        on = cam.grid != GridType.OFF,
                        enabled = true,
                        icon = if (cam.grid != GridType.OFF) Icons.Filled.GridOn else Icons.Filled.GridOff,
                        onClick = {
                            cam.grid = when (cam.grid) {
                                GridType.OFF -> GridType.THIRDS
                                GridType.THIRDS -> GridType.CROSSHAIR
                                GridType.CROSSHAIR -> GridType.BOTH
                                GridType.BOTH -> GridType.OFF
                            }
                        },
                    )
                    val hdrSupported = if (mode == CaptureMode.PHOTO) caps.hdrPhoto else caps.videoColors.size > 1
                    val hdrOn = bind?.hdrLabel != null
                    CameraChip("HDR", hdrOn, hdrSupported && !isRecording, icon = Icons.Filled.HdrOn, onClick = {
                        if (mode == CaptureMode.PHOTO) cam.hdrPhoto = !cam.hdrPhoto
                        else cam.videoColor = if (cam.videoColor != VideoColor.SDR) VideoColor.SDR
                        else caps.videoColors.firstOrNull { it != VideoColor.SDR } ?: VideoColor.SDR
                    })
                    if (mode == CaptureMode.PHOTO) {
                        CameraChip("RAW", bind?.format != null && bind?.format != PhotoFormat.JPEG, caps.anyRaw, icon = Icons.Filled.RawOn, onClick = {
                            cam.photoFormat = if (cam.photoFormat == PhotoFormat.JPEG) {
                                if (caps.rawJpeg) PhotoFormat.RAW_JPEG else PhotoFormat.RAW
                            } else PhotoFormat.JPEG
                        })
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
                        CameraChip("Punch in", cam.attendanceMode, !isRecording, icon = Icons.Filled.Badge, onClick = {
                            toggleAttendanceMode()
                        })
                        CameraChip("PiP", cam.pipMode, lenses.size > 1 && !isRecording, icon = Icons.Filled.PictureInPicture, onClick = {
                            cam.pipMode = !cam.pipMode
                        })
                        CameraChip("Document", cam.documentMode, !isRecording, icon = Icons.Filled.Description, onClick = {
                            cam.documentMode = !cam.documentMode
                        })
                    }
                    if (!mode.isVideo) {
                        CameraChip("Voice note", false, !isRecording && !busy, icon = Icons.Filled.Mic, onClick = {
                            startVoiceNote()
                        })
                        CameraChip("Scan QR", false, !isRecording && !busy, icon = Icons.Filled.QrCodeScanner, onClick = {
                            scanBarcode()
                        })
                        CameraChip("Read sign", false, !isRecording && !busy, icon = Icons.Filled.TextFields, onClick = {
                            scanSignboardText()
                        })
                        CameraChip("AI caption", false, !isRecording && !busy, icon = Icons.Filled.AutoAwesome, onClick = {
                            autoCaptionScene()
                        })
                        if (lastThumb != null) {
                            CameraChip("Ghost", showGhostOverlay, true, icon = Icons.Filled.Layers, onClick = {
                                showGhostOverlay = !showGhostOverlay
                            })
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun GeofenceBanner(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        container: Color,
        content: Color,
        action: String? = null,
        actionColor: Color = MaterialTheme.colorScheme.primary,
        onAction: (() -> Unit)? = null,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(container)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(
                text,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Text(
                    action,
                    color = actionColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = if (onAction != null) {
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onAction)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else Modifier,
                )
            }
        }
    }

    @Composable
    fun GeofenceSection(isLandscape: Boolean) {
        val activeSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
            geoMgr.findActiveSite(currentLat, currentLon)
        }
        val nearestSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
            geoMgr.findNearestSite(currentLat, currentLon)
        }
        val pinHere = {
            if (currentLat != null && currentLon != null) {
                settings.siteLat = currentLat
                settings.siteLon = currentLon
                Toast.makeText(context, "Site pin set to current location", Toast.LENGTH_SHORT).show()
            }
        }

        if (isLandscape) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = landscapeTopInset + 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (activeSitePair != null) {
                    val (site, dist) = activeSitePair
                    CameraPill(
                        text = "${site.name} · ${dist.toInt()} m",
                        icon = Icons.Filled.CheckCircle,
                        iconTint = CameraColors.InsideFenceAccent,
                        container = CameraColors.InsideFence,
                    )
                } else if (nearestSitePair != null) {
                    val (site, dist) = nearestSitePair
                    CameraPill(
                        text = "Outside ${site.name} · ${dist.toInt()} m",
                        icon = Icons.Filled.Warning,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        } else {
            if (activeSitePair != null) {
                val (site, dist) = activeSitePair
                GeofenceBanner(
                    text = "In ${site.name} · ${dist.toInt()} m",
                    icon = Icons.Filled.CheckCircle,
                    container = CameraColors.InsideFence,
                    content = CameraColors.OnScrim,
                    action = "AUTO-TAGGED",
                    actionColor = CameraColors.InsideFenceAccent,
                )
            } else if (nearestSitePair != null) {
                val (site, dist) = nearestSitePair
                GeofenceBanner(
                    text = "Outside ${site.name} · ${dist.toInt()} m (limit ${site.radiusMeters.toInt()} m)",
                    icon = Icons.Filled.Warning,
                    container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else if (geofenceDistanceM != null) {
                val outside = geofenceDistanceM > siteRadius
                GeofenceBanner(
                    text = if (outside) "Outside ${siteName.ifBlank { "site" }} · ${geofenceDistanceM.toInt()} m (limit ${siteRadius.toInt()} m)"
                    else "In site geofence · ${geofenceDistanceM.toInt()} m",
                    icon = if (outside) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    container = if (outside) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f) else CameraColors.InsideFence,
                    content = if (outside) MaterialTheme.colorScheme.onErrorContainer else CameraColors.OnScrim,
                    action = "PIN HERE",
                    onAction = pinHere,
                )
            } else if (settings.siteName.isNotBlank() && currentLat != null && currentLon != null && settings.siteLat == null) {
                GeofenceBanner(
                    text = "Site: ${settings.siteName} (no pin)",
                    icon = Icons.Filled.Place,
                    container = CameraColors.ScrimStrong,
                    content = CameraColors.OnScrim,
                    action = "SET PIN",
                    onAction = pinHere,
                )
            }
        }
    }

    @Composable
    fun CameraViewfinder(modifier: Modifier = Modifier) {
        BoxWithConstraints(modifier) {
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
                            val frac = if (isLandscape) 0.38f else (ov.width / 1080f).coerceIn(0.65f, 0.98f)
                            val margin = maxHeight * 0.02f
                            Image(
                                bitmap = ov.asImageBitmap(),
                                contentDescription = "Stamp preview",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .align(
                                        if (isLandscape) Alignment.BottomStart
                                        else when (config.position) {
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
                                    .padding(horizontal = if (isLandscape) 12.dp else 8.dp, vertical = margin)
                                    .fillMaxWidth(frac),
                            )
                        }
                    }
                }
            }

            if (cam.level) LevelIndicator(rememberRollDegrees(cam.level))
            if (cam.histogram) {
                HistogramView(
                    histogram,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = if (isLandscape) 12.dp else 8.dp, top = if (isLandscape) landscapeTopInset + 8.dp else 8.dp),
                )
            }
            if (isRecording) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isLandscape) landscapeTopInset + 8.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecTimer(recSeconds)
                    if (cam.allowVideoTagging) {
                        CameraChip("Tag (${taggedMoments.size})", on = true, enabled = true, icon = Icons.Filled.BookmarkAdd, onClick = {
                            taggedMoments.add(recSeconds)
                            Toast.makeText(context, "Tag marked at ${recSeconds}s", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
            if (isTimelapseActive) {
                CameraPill(
                    text = "Time-lapse · frame $timelapseFrameCount · every ${cam.timelapseIntervalSec}s",
                    dotColor = CameraColors.Recording,
                    container = CameraColors.ScrimStrong,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isLandscape) landscapeTopInset + 8.dp else 8.dp),
                )
            }
            if (mode == CaptureMode.VIDEO && bind?.ok == true && bind?.stampOnVideo == false) {
                CameraPill(
                    text = "No stamp on video",
                    icon = Icons.Filled.Info,
                    container = CameraColors.ErrorScrim,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }

            if (timerCountdown != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(CameraColors.Scrim),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$timerCountdown",
                            color = CameraColors.OnScrim,
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = 112.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Spacer(Modifier.height(24.dp))
                        FilledTonalButton(
                            onClick = {
                                timerJob?.cancel()
                                timerCountdown = null
                            },
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            if (busy) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .background(CameraColors.ScrimStrong, MaterialTheme.shapes.large)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(busyText, color = CameraColors.OnScrim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let {
                Text(
                    it,
                    color = CameraColors.OnScrim,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .background(CameraColors.ErrorScrim, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    @Composable
    fun LensOrZoomSelector(allowZoomPresets: Boolean = true) {
        if (lenses.size > 1) {
            CameraSegmented(
                labels = lenses.map { it.shortLabel },
                selected = selected,
                onSelect = { selected = it },
                enabled = !busy && !isRecording,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        } else if (allowZoomPresets && zoomMax > 1.01f && openPanel == null) {
            val presets = (listOf(zoomMin) + listOf(1f, 2f, 3f, 5f, 10f))
                .filter { it in zoomMin..zoomMax }
                .distinctBy { (it * 10).toInt() }
            CameraSegmented(
                labels = presets.map { z -> if (z < 1f) String.format(Locale.US, "%.1f×", z) else "${z.toInt()}×" },
                selected = presets.indexOfFirst { kotlin.math.abs(zoomNow - it) < 0.05f },
                onSelect = { controller.setZoomRatio(presets[it]) },
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun LastCaptureThumb(size: androidx.compose.ui.unit.Dp, showShareButton: Boolean) {
        var shareMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(size)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.5.dp, CameraColors.ScrimBorder, MaterialTheme.shapes.medium)
                    .background(CameraColors.Scrim)
                    .combinedClickable(
                        enabled = lastUri != null && !isRecording,
                        onClick = {
                            lastUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, lastMime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onLongClick = { if (!isTimelapseActive) shareMenu = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val t = lastThumb
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
            if (showShareButton && lastUri != null && !isRecording && !isTimelapseActive) {
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
                        shareLastToWhatsApp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Upload to Google Drive") },
                    leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                    onClick = {
                        shareMenu = false
                        uploadLastToGoogleDrive()
                    },
                )
            }
        }
    }

    @Composable
    fun SnapshotButton(size: androidx.compose.ui.unit.Dp) {
        CameraIconButton(
            icon = Icons.Filled.CameraAlt,
            contentDescription = "Take snapshot",
            enabled = !busy,
            onClick = { takeVideoSnapshot() },
            size = size,
        )
    }

    @Composable
    fun FlipButton(size: androidx.compose.ui.unit.Dp) {
        CameraIconButton(
            icon = Icons.Filled.Cameraswitch,
            contentDescription = "Flip camera",
            enabled = !busy && !isRecording && lenses.size > 1,
            onClick = { flipCamera() },
            size = size,
            iconSize = 24.dp,
        )
    }

    /** Mode-specific cyclers (timer and burst, time-lapse interval, slow-mo speed). */
    @Composable
    fun ModeOptionChips() {
        when (mode) {
            CaptureMode.PHOTO -> {
                CameraChip(
                    text = if (cam.timerSeconds > 0) "${cam.timerSeconds}s" else "Off",
                    on = cam.timerSeconds > 0,
                    enabled = !isRecording && !busy,
                    icon = Icons.Filled.Timer,
                    onClick = {
                        cam.timerSeconds = when (cam.timerSeconds) {
                            0 -> 3
                            3 -> 5
                            5 -> 10
                            else -> 0
                        }
                    },
                )
                CameraChip(
                    text = "${cam.burstCount}×",
                    on = cam.burstCount > 1,
                    enabled = !isRecording && !busy,
                    icon = Icons.Filled.BurstMode,
                    onClick = {
                        cam.burstCount = when (cam.burstCount) {
                            1 -> 3
                            3 -> 5
                            else -> 1
                        }
                    },
                )
            }
            CaptureMode.TIMELAPSE -> {
                CameraChip("Every ${cam.timelapseIntervalSec}s", true, !isTimelapseActive, icon = Icons.Filled.Timer, onClick = {
                    cam.timelapseIntervalSec = when (cam.timelapseIntervalSec) {
                        1 -> 2
                        2 -> 5
                        5 -> 10
                        10 -> 30
                        else -> 1
                    }
                })
            }
            CaptureMode.SLOW_MO -> {
                CameraChip(cam.slowMoSpeed.label, true, !isRecording, icon = Icons.Filled.SlowMotionVideo, onClick = {
                    cam.slowMoSpeed = when (cam.slowMoSpeed) {
                        SlowMoSpeed.X2 -> SlowMoSpeed.X4
                        SlowMoSpeed.X4 -> SlowMoSpeed.X8
                        SlowMoSpeed.X8 -> SlowMoSpeed.X2
                    }
                })
            }
            CaptureMode.VIDEO -> Unit
        }
    }

    @Composable
    fun PortraitBottomDeck() {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Lens or zoom presets, with the mode's quick options beside them
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LensOrZoomSelector()
                ModeOptionChips()
            }

            // Capture mode switcher
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
            ) {
                CaptureMode.entries.forEach { m ->
                    CameraModeLabel(
                        text = m.displayName,
                        active = mode == m,
                        enabled = !busy && !isRecording,
                        onClick = {
                            mode = m
                            openPanel = null
                        },
                    )
                }
            }

            // Thumbnail · shutter · flip, in equal slots so the shutter stays centered
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    LastCaptureThumb(56.dp, showShareButton = true)
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
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (isRecording) SnapshotButton(52.dp) else FlipButton(52.dp)
                }
            }
        }
    }

    @Composable
    fun LandscapeRightDeck() {
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
            // Top: mode switcher
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CaptureMode.entries.forEach { m ->
                    CameraModeLabel(
                        text = m.displayName,
                        active = mode == m,
                        enabled = !busy && !isRecording,
                        onClick = {
                            mode = m
                            openPanel = null
                        },
                        horizontalPadding = 4.dp,
                    )
                }
            }

            // Center: lens/zoom, shutter, mode options
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LensOrZoomSelector(allowZoomPresets = false)
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModeOptionChips()
                }
            }

            // Bottom: thumbnail and flip / snapshot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LastCaptureThumb(44.dp, showShareButton = false)
                if (isRecording) SnapshotButton(44.dp) else FlipButton(44.dp)
            }
        }
    }

    if (isLandscape) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                CameraViewfinder(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .aspectRatio(activeNativeRatio),
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(CameraColors.ScrimStrong, Color.Transparent)))
                        .statusBarsPadding(),
                ) {
                    TopBarContent(isLandscape = true)
                }

                GeofenceSection(isLandscape = true)

                if (showProBar) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = landscapeTopInset),
                    ) {
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
                }

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

            LandscapeRightDeck()
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                TopBarContent(isLandscape = false)
            }

            GeofenceSection(isLandscape = false)

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

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                CameraViewfinder(
                    Modifier
                        .align(Alignment.Center)
                        .aspectRatio(activeNativeRatio),
                )

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

            PortraitBottomDeck()
        }
    }
}

private val CaptureMode.displayName
    get() = when (this) {
        CaptureMode.PHOTO -> "Photo"
        CaptureMode.VIDEO -> "Video"
        CaptureMode.TIMELAPSE -> "Time-lapse"
        CaptureMode.SLOW_MO -> "Slow-mo"
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
    val onClick = when (mode) {
        CaptureMode.PHOTO -> onPhotoClick
        CaptureMode.TIMELAPSE -> onTimelapseClick
        CaptureMode.VIDEO, CaptureMode.SLOW_MO -> onVideoClick
    }
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
