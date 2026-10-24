package com.geostamp.camera.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.SlowMoSpeed
import com.geostamp.camera.data.SnapshotMode
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.StampTemplate
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.location.FixIssue
import com.geostamp.camera.stamp.FrameWatermarkRenderer
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.ShotParams
import com.geostamp.camera.stamp.StampedCapture
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Things the screen has to do with an Activity, which the ViewModel doesn't hold. */
sealed interface CameraEvent {
    data class UploadToDrive(val uri: Uri, val mime: String) : CameraEvent
}

/**
 * State and actions of the camera screen: lens and mode selection, binding, capture (photo, burst,
 * timer, RAW, PiP, time-lapse, video), the live stamp preview and the scan/OCR/caption helpers.
 * The screen only lays it out and forwards gestures; everything that needs an Activity
 * (permission and speech launchers, share intents) stays in the UI.
 */
class CameraViewModel(
    context: Context,
    val settings: StampSettings,
    val cam: CameraSettings,
    val repo: StampDataRepository,
    val controller: CameraController,
    val geofences: GeofenceManager,
) : ViewModel() {
    private val app = context.applicationContext

    val manual = ManualState()
    val taggedMoments = mutableStateListOf<Long>()

    var lenses by mutableStateOf<List<LensInfo>>(emptyList())
        private set
    var selected by mutableIntStateOf(if (cam.preservePortraitFocalLength) cam.savedLensIndex else 0)
    var mode by mutableStateOf(
        if (cam.preserveCameraMode) {
            runCatching { CaptureMode.valueOf(cam.savedCameraMode) }.getOrDefault(CaptureMode.PHOTO)
        } else CaptureMode.PHOTO
    )
        private set
    var bind by mutableStateOf<BindResult?>(null)
        private set
    var caps by mutableStateOf(LensCaps())
        private set
    var sensor by mutableStateOf(SensorInfo())
        private set
    var actualRes by mutableStateOf<Size?>(null)
        private set
    var zoomMin by mutableFloatStateOf(1f)
        private set
    var zoomMax by mutableFloatStateOf(1f)
        private set
    var zoomNow by mutableFloatStateOf(1f)
        private set
    var flash by mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)
        private set
    var busy by mutableStateOf(false)
        private set
    var busyText by mutableStateOf("")
        private set
    var lastThumb by mutableStateOf<Bitmap?>(null)
        private set
    var lastUri by mutableStateOf<Uri?>(null)
        private set
    var lastMime by mutableStateOf("image/jpeg")
        private set
    var overlay by mutableStateOf<Bitmap?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var openPanel by mutableStateOf<Panel?>(null)
    var showProBar by mutableStateOf(false)
        private set
    var showToolsBar by mutableStateOf(false)
    var recording by mutableStateOf<Recording?>(null)
        private set
    var recSeconds by mutableLongStateOf(0L)
        private set
    var isTimelapseActive by mutableStateOf(false)
        private set
    var timelapseFrameCount by mutableIntStateOf(0)
        private set
    /** Ticks every second in video mode so the stamp's clock (burned into each frame) moves. */
    var tick by mutableIntStateOf(0)
        private set
    /** The phone is held sideways (from the orientation sensor, not the screen layout). */
    var landscape by mutableStateOf(false)
        private set
    var micGranted by mutableStateOf(
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
    var timerCountdown by mutableStateOf<Int?>(null)
        private set
    var showGhostOverlay by mutableStateOf(cam.ghostOverlay)
    var ghostAlpha by mutableFloatStateOf(0.4f)

    private var timelapseJob: Job? = null
    private var timerJob: Job? = null

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    val isRecording get() = recording != null
    val currentLens get() = lenses.getOrNull(selected)

    val photoSpec
        get() = PhotoSpec(
            hdr = cam.hdrPhoto,
            extension = cam.photoExtension,
            format = cam.photoFormat,
            aspect = cam.photoAspect,
            resolution = cam.photoResolution,
            histogram = cam.histogram,
            focusPeaking = cam.focusPeaking,
            exposureVerification = cam.exposureVerification,
        )

    private val videoSpec
        get() = VideoSpec(
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

    val effectiveVideoSpec
        get() = if (mode == CaptureMode.SLOW_MO) videoSpec.copy(fps = cam.slowMoSpeed.targetFps) else videoSpec

    /** What the camera is bound with; the screen rebinds whenever this changes. */
    val spec: Any get() = if (mode.isVideo) effectiveVideoSpec else photoSpec

    private val manualFps get() = if (mode.isVideo) effectiveVideoSpec.fps else 30

    val nativeRatio get() = bind?.nativeRatio ?: (4f / 3f)
    val photoCrop get() = !mode.isVideo && cam.photoAspect.cropped && bind?.format != PhotoFormat.RAW
    /** Long / short side of the frame that gets saved. */
    val frameLongShort get() = if (photoCrop) cam.photoAspect.ratio else nativeRatio

    val formatText: String
        get() = if (mode == CaptureMode.SLOW_MO) {
            "SLOW-MO ${cam.slowMoSpeed.label} ${videoResLabel(cam.videoRes)} ${cam.videoAspect.label}"
        } else if (mode == CaptureMode.PHOTO) {
            val f = when (bind?.format) {
                PhotoFormat.RAW_JPEG -> "RAW+J"
                PhotoFormat.RAW -> "RAW"
                else -> "JPG"
            }
            val mp = actualRes?.let { String.format(Locale.US, " %.0fMP", it.width.toLong() * it.height / 1_000_000.0) } ?: ""
            "$f ${cam.photoAspect.label}$mp" + (bind?.hdrLabel?.let { " $it" } ?: "")
        } else {
            val color = when (bind?.color) { VideoColor.HLG -> " HLG"; VideoColor.HDR10 -> " HDR10"; VideoColor.HDR10_PLUS -> " HDR10+"; VideoColor.DOLBY_VISION -> " DV"; else -> "" }
            "${videoResLabel(cam.videoRes)} ${cam.videoAspect.label}$color"
        }

    init {
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

        // Find every camera on the phone.
        viewModelScope.launch {
            try {
                lenses = controller.init()
                if (lenses.isEmpty()) error = "No cameras found"
            } catch (e: Exception) {
                error = "Camera error: ${e.message}"
            }
        }

        // Remember mode, lens and manual settings when the user asked for that.
        viewModelScope.launch {
            snapshotFlow { mode }.collect { if (cam.preserveCameraMode) cam.savedCameraMode = it.name }
        }
        viewModelScope.launch {
            snapshotFlow { selected }.collect { if (cam.preservePortraitFocalLength) cam.savedLensIndex = it }
        }
        viewModelScope.launch {
            snapshotFlow { manual.ev }.collect { if (cam.preserveExposureCompensation) cam.savedEv = it }
        }
        viewModelScope.launch {
            snapshotFlow { listOf(manual.iso, manual.shutterNs, manual.wb, manual.kelvin, manual.focusDiopters) }.collect {
                if (cam.preserveParameters) {
                    cam.savedIso = manual.iso ?: -1
                    cam.savedShutterNs = manual.shutterNs ?: -1L
                    cam.savedWb = manual.wb.name
                    cam.savedKelvin = manual.kelvin
                    cam.savedFocusDiopters = manual.focusDiopters ?: -1f
                }
            }
        }

        // Push manual ISO / shutter / WB / focus / EV whenever they change.
        viewModelScope.launch {
            snapshotFlow { listOf(manual.iso, manual.shutterNs, manual.wb, manual.kelvin, manual.tint, manual.focusDiopters, manual.ev, bind) }
                .collect { controller.applyManual(manual, manualFps) }
        }

        // The sound-level meter and the video recorder can't share the microphone reliably,
        // so the meter pauses while recording.
        viewModelScope.launch {
            snapshotFlow { isRecording }.collect {
                repo.setSoundEnabled(!it && settings.isEnabled(StampField.SOUND_LEVEL))
            }
        }

        // Resolution actually picked by the camera (shown in the info bar).
        viewModelScope.launch {
            snapshotFlow { bind }.collectLatest {
                delay(800)
                actualRes = controller.currentResolution()
            }
        }

        // In video mode the stamp's clock ticks every second.
        viewModelScope.launch {
            snapshotFlow { mode }.collectLatest { m ->
                while (m.isVideo) {
                    delay(1000)
                    tick++
                }
            }
        }
    }

    override fun onCleared() {
        // Release the camera (and stop any recording) when leaving the screen.
        recording?.stop()
        controller.unbind()
    }

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(app, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // ---- Binding -------------------------------------------------------------------------------

    /** Binds the chosen lens with the chosen mode and settings, and follows its zoom range. Returns the cleanup. */
    fun bindCamera(owner: LifecycleOwner): () -> Unit {
        val lens = currentLens
        var observer: Observer<ZoomState>? = null
        if (lens != null) {
            val r = if (mode.isVideo) controller.bindVideo(owner, lens, effectiveVideoSpec, targetMode = mode)
            else controller.bindPhoto(owner, lens, photoSpec)
            bind = r
            caps = controller.capabilities(lens)
            sensor = controller.sensorInfo
            error = if (r.ok) null else (r.notice ?: "Couldn't open ${lens.shortLabel}")
            if (r.ok && r.notice != null) toast(r.notice, long = true)
            controller.applyManual(manual, manualFps)
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
        return { observer?.let { o -> cam0?.cameraInfo?.zoomState?.removeObserver(o) } }
    }

    fun onDeviceRotation(orientation: Int) {
        val rot = when (orientation) {
            in 45 until 135 -> Surface.ROTATION_270
            in 135 until 225 -> Surface.ROTATION_180
            in 225 until 315 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
        controller.targetRotation = rot
        landscape = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
    }

    // ---- Stamp preview -------------------------------------------------------------------------

    /** Live stamp preview, drawn by the same renderer used for saving. In video mode it's also burned into the recording. */
    suspend fun renderStampPreview(config: StampConfig, isLandscape: Boolean) {
        val lens = currentLens
        val lensLabel = lens?.label
        val longShort = frameLongShort
        val video = mode.isVideo
        val data = repo.data.value
        val readout = controller.readout.value
        val bmp = withContext(Dispatchers.Default) {
            val w = if (isLandscape) (1080 * longShort).toInt().coerceAtLeast(600) else 1080
            val h = if (isLandscape) 1080 else (1080 * longShort).toInt().coerceAtLeast(600)
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
                    preview = !video,
                )
            }
        }
        overlay = bmp
        controller.setVideoStamp(if (video) bmp else null, config.size, config.position == StampPosition.TOP)
    }

    fun cycleStampPosition() {
        settings.position = CameraCycles.nextStampPosition(settings.position)
    }

    // ---- Top bar and quick tools ---------------------------------------------------------------

    fun cycleFlash() {
        flash = CameraCycles.nextFlash(flash)
        controller.flashMode = flash
    }

    fun refreshLocation() {
        repo.refreshAll()
        toast("Refreshing location")
    }

    fun toggleProBar() {
        showProBar = !showProBar
        if (!showProBar) openPanel = null
    }

    fun requestPanel(p: Panel) {
        if (isRecording && (p == Panel.FPS || p == Panel.FORMAT)) {
            toast("Stop recording to change this")
        } else {
            openPanel = p
        }
    }

    fun selectMode(m: CaptureMode) {
        mode = m
        openPanel = null
    }

    fun cycleGrid() {
        cam.grid = CameraCycles.nextGrid(cam.grid)
    }

    fun toggleHdr() {
        if (mode == CaptureMode.PHOTO) cam.hdrPhoto = !cam.hdrPhoto
        else cam.videoColor = if (cam.videoColor != VideoColor.SDR) VideoColor.SDR
        else caps.videoColors.firstOrNull { it != VideoColor.SDR } ?: VideoColor.SDR
    }

    fun toggleRaw() {
        cam.photoFormat = if (cam.photoFormat == PhotoFormat.JPEG) {
            if (caps.rawJpeg) PhotoFormat.RAW_JPEG else PhotoFormat.RAW
        } else PhotoFormat.JPEG
    }

    fun cycleTimer() {
        cam.timerSeconds = CameraCycles.nextTimerSeconds(cam.timerSeconds)
    }

    fun cycleBurst() {
        cam.burstCount = CameraCycles.nextBurstCount(cam.burstCount)
    }

    fun cycleTimelapseInterval() {
        cam.timelapseIntervalSec = CameraCycles.nextTimelapseInterval(cam.timelapseIntervalSec)
    }

    fun cycleSlowMoSpeed() {
        cam.slowMoSpeed = CameraCycles.nextSlowMoSpeed(cam.slowMoSpeed)
    }

    fun onVoiceNote(text: String) {
        settings.note = text
        toast("Voice Note: $text")
    }

    fun toggleAttendanceMode() {
        cam.attendanceMode = !cam.attendanceMode
        if (cam.attendanceMode) {
            val frontIdx = lenses.indexOfFirst { it.isFront }
            if (frontIdx >= 0) selected = frontIdx
            settings.applyTemplate(StampTemplate.ATTENDANCE)
            settings.note = "ATTENDANCE VERIFIED"
            settings.setEnabled(StampField.QR_CODE, true)
            toast("Attendance Punch Mode ON")
        } else {
            val backIdx = lenses.indexOfFirst { !it.isFront }
            if (backIdx >= 0) selected = backIdx
            settings.applyTemplate(StampTemplate.STANDARD)
            settings.note = "Captured by GeoStamp Camera"
            toast("Standard Mode")
        }
    }

    fun flipCamera() {
        if (busy || isRecording || lenses.size <= 1) return
        val targetIdx = if (currentLens?.isFront == true) {
            lenses.indexOfFirst { !it.isFront }
        } else {
            lenses.indexOfFirst { it.isFront }
        }
        selected = if (targetIdx >= 0) targetIdx else (selected + 1) % lenses.size
    }

    // ---- Geofences -----------------------------------------------------------------------------

    /** Auto-switch project tags when entering a saved site's boundary. */
    fun autoSwitchSite(lat: Double?, lon: Double?) {
        if (lat != null && lon != null && geofences.autoSwitchEnabled.value) {
            val active = geofences.findActiveSite(lat, lon)
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

    fun pinSiteHere(lat: Double?, lon: Double?) {
        if (lat != null && lon != null) {
            settings.siteLat = lat
            settings.siteLon = lon
            toast("Site pin set to current location")
        }
    }

    // ---- Photo capture -------------------------------------------------------------------------

    private fun warnIfNoGps() {
        val d = repo.data.value
        val age = d.fixElapsedMs?.let { SystemClock.elapsedRealtime() - it }
        val issues = FixCheck.issues(d.latitude != null, age, d.accuracyM, d.isMock)
        FixCheck.warning(issues, age, d.accuracyM)?.let {
            toast(it, long = FixIssue.NO_FIX !in issues)
        }
    }

    private fun shotParamsFor(lens: LensInfo, cfg: StampConfig): ShotParams {
        val readout = controller.readout.value
        return ShotParams(
            focalMm = lens.equivFocalMm,
            aperture = lens.aperture,
            shutterNs = readout.exposureNs,
            iso = readout.iso,
            model = cfg.deviceBrand.ifBlank { null },
        )
    }

    private fun stampDataFor(lens: LensInfo) = repo.data.value.let { d0 ->
        if (lens.isFront) d0.copy(azimuthDeg = d0.azimuthDeg?.let { (it + 180f) % 360f }) else d0
    }

    /**
     * [cameraJpeg] is the camera's own JPEG: its EXIF is carried over, and it's kept as the original if asked.
     * [cameraRotation] is CameraX's rotation for an in-memory JPEG, null when the file's EXIF already has it.
     */
    private suspend fun saveStamped(bmp0: Bitmap, lens: LensInfo, cameraJpeg: ByteArray? = null, cameraRotation: Int? = null) {
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
        val result = withContext(Dispatchers.Default) { StampedCapture.save(app, request) }
        settings.nextNumber = settings.nextNumber + 1
        val uri = result.uri
        lastUri = uri
        lastMime = "image/jpeg"
        lastThumb = result.thumbnail
        if (uri == null) {
            toast("Couldn't save photo")
        } else if (cam.autoDriveUpload) {
            _events.trySend(CameraEvent.UploadToDrive(uri, lastMime))
        }
    }

    private suspend fun capturePhoto(owner: LifecycleOwner, lens: LensInfo) {
        val location = repo.lastLocation
        when (bind?.format ?: PhotoFormat.JPEG) {
            PhotoFormat.RAW -> {
                val uri = controller.takeRawOnly(location)
                lastUri = uri
                lastMime = "image/x-adobe-dng"
                lastThumb = null
                toast(if (uri != null) "RAW (DNG) saved – RAW files can't carry a stamp" else "RAW capture failed")
            }
            PhotoFormat.RAW_JPEG -> {
                val tmp = File(app.cacheDir, "pair_${System.nanoTime()}.jpg")
                try {
                    controller.takeRawAndJpeg(tmp, location)
                    val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeFile(app, tmp) }
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

    private suspend fun executeCapture(owner: LifecycleOwner, lens: LensInfo, count: Int) {
        busy = true
        try {
            for (i in 1..count) {
                busyText = if (count > 1) "Burst $i/$count…" else "Saving…"
                capturePhoto(owner, lens)
                if (i < count) delay(300)
            }
        } catch (e: Exception) {
            toast("Capture failed: ${e.message}")
        } finally {
            busy = false
        }
    }

    private fun startCountdown(seconds: Int, then: suspend () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (s in seconds downTo 1) {
                timerCountdown = s
                delay(1000)
            }
            timerCountdown = null
            then()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerCountdown = null
    }

    fun shoot(owner: LifecycleOwner) {
        val lens = currentLens ?: return
        if (busy) return
        warnIfNoGps()
        val timer = cam.timerSeconds
        val burst = cam.burstCount.coerceAtLeast(1)
        if (timer > 0) {
            startCountdown(timer) { executeCapture(owner, lens, burst) }
        } else {
            viewModelScope.launch { executeCapture(owner, lens, burst) }
        }
    }

    fun shootAllLenses(owner: LifecycleOwner) {
        if (busy || lenses.isEmpty()) return
        busy = true
        viewModelScope.launch {
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
                        capturePhoto(owner, lens)
                        saved++
                    } catch (e: Exception) {
                        // skip lenses that can't capture (e.g. depth sensors)
                    }
                }
            } finally {
                currentLens?.let { bind = controller.bindPhoto(owner, it, photoSpec) }
                controller.applyManual(manual, 30)
                busy = false
                toast("Saved $saved photo(s) from ${lenses.size} cameras", long = true)
            }
        }
    }

    /** Takes a frame, runs [analyze] on it off the main thread and hands the result to [onResult]. */
    private fun analyzeFrame(
        label: String,
        errorPrefix: String,
        analyze: suspend (Bitmap) -> String?,
        onResult: (String?) -> Unit,
    ) {
        if (busy) return
        busy = true
        busyText = label
        viewModelScope.launch {
            try {
                val photo = controller.takePhoto()
                val bmp = withContext(Dispatchers.Default) { PhotoComposer.decodeCameraJpeg(photo.jpeg, photo.rotationDegrees) }
                val result = withContext(Dispatchers.Default) { analyze(bmp) }
                bmp.recycle()
                onResult(result)
            } catch (e: Exception) {
                toast("$errorPrefix: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun scanBarcode() = analyzeFrame(
        "Scanning Barcode…", "Scan error",
        { com.geostamp.camera.stamp.BarcodeScanner.scan(it) },
    ) { code ->
        if (!code.isNullOrBlank()) {
            settings.note = "Asset: $code"
            toast("Scanned: $code", long = true)
        } else {
            toast("No QR or Barcode detected in frame")
        }
    }

    fun scanSignboardText() = analyzeFrame(
        "Reading Signboard Text…", "OCR error",
        { com.geostamp.camera.ai.VisionAnalyzer.recognizeText(it) },
    ) { detected ->
        if (!detected.isNullOrBlank()) {
            val firstLine = detected.lines().firstOrNull { it.isNotBlank() } ?: detected
            settings.note = firstLine.take(80)
            toast("Detected text: $firstLine", long = true)
        } else {
            toast("No text detected in view")
        }
    }

    fun autoCaptionScene() = analyzeFrame(
        "Generating Scene Caption…", "Caption error",
        { com.geostamp.camera.ai.VisionAnalyzer.autoCaption(it) },
    ) { caption ->
        if (!caption.isNullOrBlank()) {
            settings.note = caption
            toast("Caption: $caption", long = true)
        } else {
            toast("Could not identify scene")
        }
    }

    // ---- Time-lapse ----------------------------------------------------------------------------

    fun toggleTimelapse() {
        if (isTimelapseActive) {
            timelapseJob?.cancel()
            timelapseJob = null
            isTimelapseActive = false
            toast("Timelapse ended. $timelapseFrameCount frames captured.", long = true)
            return
        }
        val lens = currentLens ?: return
        isTimelapseActive = true
        timelapseFrameCount = 0
        val sessionTag = "Timelapse_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val sub = if (settings.siteName.isNotBlank()) "${settings.siteName}/$sessionTag" else sessionTag
        timelapseJob = viewModelScope.launch {
            toast("Timelapse started (every ${cam.timelapseIntervalSec}s)")
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
                    val frame = withContext(Dispatchers.Default) { StampedCapture.save(app, request) }
                    lastUri = frame.uri
                    lastMime = "image/jpeg"
                    lastThumb = frame.thumbnail
                    timelapseFrameCount++
                    settings.nextNumber = settings.nextNumber + 1
                    delay(cam.timelapseIntervalSec * 1000L)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                toast("Timelapse stopped: ${e.message}")
            } finally {
                isTimelapseActive = false
            }
        }
    }

    // ---- Video ---------------------------------------------------------------------------------

    fun takeVideoSnapshot() {
        if (busy) return
        viewModelScope.launch {
            val lens = currentLens ?: return@launch
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
                toast("Snapshot saved")
            } catch (e: Exception) {
                toast("Snapshot failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun tagMoment() {
        taggedMoments.add(recSeconds)
        toast("Tag marked at ${recSeconds}s")
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
                is VideoRecordEvent.Finalize -> onRecordingFinalized(event)
                else -> Unit
            }
        }
        recording = r
        if (r == null) toast("Video isn't ready")
    }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        recording = null
        recSeconds = 0
        val uri = event.outputResults.outputUri
        if (uri != Uri.EMPTY) {
            settings.nextNumber = settings.nextNumber + 1
            lastUri = uri
            lastMime = "video/mp4"
            viewModelScope.launch {
                lastThumb = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runCatching { app.contentResolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()
                    } else null
                }
            }
            if (taggedMoments.isNotEmpty()) {
                val tagsList = taggedMoments.toList()
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val tagJson = org.json.JSONArray().apply {
                            tagsList.forEach { put(it) }
                        }.toString()
                        val tagFile = File(app.getExternalFilesDir(null), "GeoStamp_Video_${System.currentTimeMillis()}.tags.json")
                        tagFile.writeText(tagJson)
                    }
                }
                taggedMoments.clear()
            }
            toast("Video saved to Movies/GeoStamp")
        } else {
            taggedMoments.clear()
            toast("Recording failed (error ${event.error})", long = true)
        }
    }

    // ---- Shutter and volume keys ---------------------------------------------------------------

    fun onShutter(owner: LifecycleOwner) {
        when (mode) {
            CaptureMode.PHOTO -> shoot(owner)
            CaptureMode.VIDEO, CaptureMode.SLOW_MO -> toggleRecording()
            CaptureMode.TIMELAPSE -> toggleTimelapse()
        }
    }

    fun onVolumeKey(event: VolumeKeyEvent, owner: LifecycleOwner) {
        if (busy) return
        when (cam.volumeButtonAction) {
            VolumeButtonAction.SHUTTER -> onShutter(owner)
            VolumeButtonAction.TIMER_2S -> {
                if (mode == CaptureMode.PHOTO) {
                    startCountdown(2) {
                        val lens = currentLens ?: return@startCountdown
                        executeCapture(owner, lens, cam.burstCount.coerceAtLeast(1))
                    }
                } else if (mode.isVideo) {
                    toggleRecording()
                }
            }
            VolumeButtonAction.ZOOM -> {
                val zs = controller.camera?.cameraInfo?.zoomState?.value ?: return
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

    /** Message sent along with a shared photo. */
    fun shareText(): String {
        val data = repo.data.value
        val config = settings.snapshot()
        return buildString {
            append("📍 GeoStamp Photo\n")
            data.title?.let { append("🏢 Location: $it\n") }
            if (data.latitude != null && data.longitude != null) {
                append("🗺 GPS: ${data.latitude}, ${data.longitude}\n")
            }
            data.plusCode?.let { append("🌐 Plus Code: $it\n") }
            append("⏰ Time: ${WatermarkRenderer.formatDate(config, ZonedDateTime.now())}\n")
            if (settings.note.isNotBlank()) append("📝 Note: ${settings.note}\n")
        }
    }

    private fun videoResLabel(r: VideoRes) = when (r) { VideoRes.UHD -> "4K"; VideoRes.FHD -> "1080"; VideoRes.HD -> "720"; VideoRes.SD -> "480" }
}

/** The tap-to-cycle steps of the camera's quick controls. */
internal object CameraCycles {
    fun nextFlash(mode: Int) = when (mode) {
        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
        else -> ImageCapture.FLASH_MODE_OFF
    }

    fun nextGrid(grid: GridType) = when (grid) {
        GridType.OFF -> GridType.THIRDS
        GridType.THIRDS -> GridType.CROSSHAIR
        GridType.CROSSHAIR -> GridType.BOTH
        GridType.BOTH -> GridType.OFF
    }

    fun nextStampPosition(p: StampPosition) = when (p) {
        StampPosition.BOTTOM -> StampPosition.TOP
        StampPosition.TOP -> StampPosition.CENTER
        StampPosition.CENTER -> StampPosition.BOTTOM
    }

    fun nextTimerSeconds(s: Int) = when (s) {
        0 -> 3
        3 -> 5
        5 -> 10
        else -> 0
    }

    fun nextBurstCount(n: Int) = when (n) {
        1 -> 3
        3 -> 5
        else -> 1
    }

    fun nextTimelapseInterval(s: Int) = when (s) {
        1 -> 2
        2 -> 5
        5 -> 10
        10 -> 30
        else -> 1
    }

    fun nextSlowMoSpeed(s: SlowMoSpeed) = when (s) {
        SlowMoSpeed.X2 -> SlowMoSpeed.X4
        SlowMoSpeed.X4 -> SlowMoSpeed.X8
        SlowMoSpeed.X8 -> SlowMoSpeed.X2
    }
}
