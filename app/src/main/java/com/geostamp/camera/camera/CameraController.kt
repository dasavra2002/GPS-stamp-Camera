package com.geostamp.camera.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.util.SizeF
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.geostamp.camera.data.Bitrate
import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.data.PhotoFormat
import com.geostamp.camera.data.StampSize
import com.geostamp.camera.data.VideoAspect
import com.geostamp.camera.data.VideoColor
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.stamp.PhotoComposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One camera the phone exposes (front, wide, ultra-wide, telephoto, macro…). */
data class LensInfo(
    val cameraId: String,
    val facing: Int,
    val equivFocalMm: Int?,
    val label: String,
    val shortLabel: String,
    val aperture: Float? = null,
) {
    val isFront get() = facing == CameraCharacteristics.LENS_FACING_FRONT
}

/** What a lens can do beyond a normal JPEG. */
data class LensCaps(
    val rawJpeg: Boolean = false,
    val rawOnly: Boolean = false,
    val ultraHdrPhoto: Boolean = false,
    val hdrExtension: Boolean = false,
    val nightExtension: Boolean = false,
    val bokehExtension: Boolean = false,
    val faceRetouchExtension: Boolean = false,
    val videoColors: List<VideoColor> = listOf(VideoColor.SDR),
    val videoResolutions: List<VideoRes> = emptyList(),
) {
    val hdrPhoto get() = hdrExtension || ultraHdrPhoto
    val anyRaw get() = rawJpeg || rawOnly
    val anyExtension get() = hdrExtension || nightExtension || bokehExtension || faceRetouchExtension
}

enum class CaptureMode { PHOTO, VIDEO, TIMELAPSE }

data class PhotoSpec(
    val hdr: Boolean,
    val extension: com.geostamp.camera.data.ExtensionModeType = com.geostamp.camera.data.ExtensionModeType.NONE,
    val format: PhotoFormat,
    val aspect: PhotoAspect,
    val resolution: String,
    val histogram: Boolean,
    val focusPeaking: Boolean = false,
    val exposureVerification: Boolean = false,
)

data class VideoSpec(
    val res: VideoRes,
    val aspect: VideoAspect,
    val fps: Int,
    val bitrate: Bitrate,
    val color: VideoColor,
    val codec: com.geostamp.camera.data.VideoCodec = com.geostamp.camera.data.VideoCodec.H264,
    val autoFrameRate: Boolean = false,
    val stabilization: Boolean,
    val histogram: Boolean,
    val focusPeaking: Boolean = false,
    val exposureVerification: Boolean = false,
)

/** What actually got switched on after binding (a lens may not support everything asked for). */
data class BindResult(
    val ok: Boolean,
    val mode: CaptureMode,
    val hdrLabel: String? = null,
    val format: PhotoFormat = PhotoFormat.JPEG,
    val stampOnVideo: Boolean = false,
    val color: VideoColor = VideoColor.SDR,
    /** Native sensor-stream ratio of the preview (long / short). */
    val nativeRatio: Float = 4f / 3f,
    val notice: String? = null,
)

class CapturedPhoto(val jpeg: ByteArray, val rotationDegrees: Int)

class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** Set by the screen when the PreviewView is created. */
    var surfaceProvider: Preview.SurfaceProvider? = null
        set(value) {
            field = value
            preview?.setSurfaceProvider(value)
        }

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var overlayEffect: OverlayEffect? = null
    var camera: Camera? = null
        private set
    var sensorInfo: SensorInfo = SensorInfo()
        private set

    var targetRotation: Int = Surface.ROTATION_0
        set(value) {
            field = value
            imageCapture?.targetRotation = value
            videoCapture?.targetRotation = value
        }

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        set(value) {
            field = value
            imageCapture?.flashMode = value
        }

    // ---------------- live readout & histogram ----------------

    private val _readout = MutableStateFlow(Readout())
    val readout: StateFlow<Readout> = _readout
    private var lastReadoutAt = 0L

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReadoutAt < 250) return
            lastReadoutAt = now
            val prev = _readout.value
            val awbAuto = result.get(CaptureResult.CONTROL_AWB_MODE) == CaptureResult.CONTROL_AWB_MODE_AUTO
            _readout.value = Readout(
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
                // Keep the camera's own colour transform from Auto WB to reuse in Kelvin mode.
                gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS),
                transform = if (awbAuto) result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) ?: prev.transform else prev.transform,
            )
        }
    }

    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram
    private var lastHistogramAt = 0L

    private val _focusPeakingBitmap = MutableStateFlow<Bitmap?>(null)
    val focusPeakingBitmap: StateFlow<Bitmap?> = _focusPeakingBitmap

    private val _exposureVerificationBitmap = MutableStateFlow<Bitmap?>(null)
    val exposureVerificationBitmap: StateFlow<Bitmap?> = _exposureVerificationBitmap

    var isFocusPeakingEnabled = false
    var isExposureVerificationEnabled = false

    private val analyzer = ImageAnalysis.Analyzer { image ->
        try {
            val now = SystemClock.elapsedRealtime()
            val plane = image.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val imgW = image.width
            val imgH = image.height

            // 1. Luminance histogram
            if (now - lastHistogramAt >= 150) {
                lastHistogramAt = now
                val bins = IntArray(64)
                var y = 0
                while (y < imgH) {
                    var x = 0
                    val row = y * rowStride
                    while (x < imgW) {
                        val v = buf.get(row + x * pixelStride).toInt() and 0xFF
                        bins[v shr 2]++
                        x += 4
                    }
                    y += 4
                }
                _histogram.value = bins
            }

            // 2. Focus Peaking & Exposure Verification
            val step = 4
            val outW = imgW / step
            val outH = imgH / step

            if (isFocusPeakingEnabled) {
                val peakPixels = IntArray(outW * outH)
                for (oy in 1 until outH - 1) {
                    val iy = oy * step
                    val row = iy * rowStride
                    val rowUp = (iy - step) * rowStride
                    val rowDown = (iy + step) * rowStride
                    for (ox in 1 until outW - 1) {
                        val ix = ox * step
                        val left = buf.get(row + (ix - step) * pixelStride).toInt() and 0xFF
                        val right = buf.get(row + (ix + step) * pixelStride).toInt() and 0xFF
                        val up = buf.get(rowUp + ix * pixelStride).toInt() and 0xFF
                        val down = buf.get(rowDown + ix * pixelStride).toInt() and 0xFF
                        val grad = kotlin.math.abs(right - left) + kotlin.math.abs(down - up)
                        if (grad > 55) {
                            peakPixels[oy * outW + ox] = 0xDD00FF44.toInt() // Neon green in-focus highlight
                        }
                    }
                }
                _focusPeakingBitmap.value = Bitmap.createBitmap(peakPixels, outW, outH, Bitmap.Config.ARGB_8888)
            } else if (_focusPeakingBitmap.value != null) {
                _focusPeakingBitmap.value = null
            }

            if (isExposureVerificationEnabled) {
                val expPixels = IntArray(outW * outH)
                for (oy in 0 until outH) {
                    val row = (oy * step) * rowStride
                    for (ox in 0 until outW) {
                        val v = buf.get(row + (ox * step) * pixelStride).toInt() and 0xFF
                        if (v < 16) {
                            expPixels[oy * outW + ox] = 0xAA0055FF.toInt() // Blue shadow clipping
                        } else if (v > 240) {
                            expPixels[oy * outW + ox] = 0xAAFF1111.toInt() // Red highlight clipping
                        }
                    }
                }
                _exposureVerificationBitmap.value = Bitmap.createBitmap(expPixels, outW, outH, Bitmap.Config.ARGB_8888)
            } else if (_exposureVerificationBitmap.value != null) {
                _exposureVerificationBitmap.value = null
            }
        } catch (e: Exception) {
            // ignore a bad frame
        } finally {
            image.close()
        }
    }

    // ---------------- setup ----------------

    suspend fun init(): List<LensInfo> {
        val p = provider ?: suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
        provider = p
        if (extensions == null) {
            extensions = try {
                suspendCancellableCoroutine<ExtensionsManager?> { cont ->
                    val future = ExtensionsManager.getInstanceAsync(context, p)
                    future.addListener({
                        cont.resume(runCatching { future.get() }.getOrNull())
                    }, ContextCompat.getMainExecutor(context))
                }
            } catch (e: Exception) {
                null
            }
        }
        return listLenses(p.availableCameraInfos)
    }

    private fun listLenses(infos: List<CameraInfo>): List<LensInfo> {
        data class Raw(val id: String, val facing: Int, val eq: Int?, val aperture: Float?)

        val raws = infos.mapNotNull { info ->
            try {
                val c2 = Camera2CameraInfo.from(info)
                val facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_EXTERNAL
                val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val aperture = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull()
                val sensor: SizeF? = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val eq = if (focal != null && sensor != null && sensor.width > 0f) {
                    val diag = sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
                    (focal * 43.27f / diag).roundToInt() // 35 mm-equivalent focal length
                } else null
                Raw(c2.cameraId, facing, eq, aperture)
            } catch (e: Exception) {
                null
            }
        }

        // The back camera closest to 26 mm is treated as the main "Wide" lens.
        val backs = raws.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        val mainEq = backs.filter { it.eq != null }.minByOrNull { abs(it.eq!! - 26) }?.eq

        val lenses = raws.map { r ->
            val eq: Int? = r.eq
            val kind = when (r.facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> if (eq == null || mainEq == null) {
                    "Back"
                } else if (eq < mainEq * 0.8f) {
                    "Ultra-wide"
                } else if (eq > mainEq * 1.5f) {
                    "Telephoto"
                } else {
                    "Wide"
                }
            }
            val zoomHint = if (r.facing == CameraCharacteristics.LENS_FACING_BACK && eq != null && mainEq != null) {
                val z = eq.toFloat() / mainEq
                if (abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}x" else String.format(Locale.US, "%.1fx", z)
            } else null
            val mm = r.eq?.let { " · ${it}mm" } ?: ""
            LensInfo(
                cameraId = r.id,
                facing = r.facing,
                equivFocalMm = r.eq,
                label = "$kind$mm (id ${r.id})",
                shortLabel = if (zoomHint != null && kind != "Back") "$kind $zoomHint" else kind,
                aperture = r.aperture,
            )
        }
        return lenses.sortedWith(
            compareBy<LensInfo>({
                when (it.facing) {
                    CameraCharacteristics.LENS_FACING_BACK -> 0
                    CameraCharacteristics.LENS_FACING_FRONT -> 1
                    else -> 2
                }
            }, { it.equivFocalMm ?: 999 }, { it.cameraId }),
        ).let { disambiguate(it) }
    }

    /** If two lenses get the same short name, number them so the chips stay distinct. */
    private fun disambiguate(list: List<LensInfo>): List<LensInfo> {
        val counts = list.groupingBy { it.shortLabel }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return list.map {
            if ((counts[it.shortLabel] ?: 0) > 1) {
                val n = (seen[it.shortLabel] ?: 0) + 1
                seen[it.shortLabel] = n
                it.copy(shortLabel = "${it.shortLabel} $n")
            } else it
        }
    }

    private fun selectorFor(lens: LensInfo): CameraSelector = CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == lens.cameraId } }
        .build()

    private fun cameraInfoFor(lens: LensInfo): CameraInfo? =
        provider?.availableCameraInfos?.firstOrNull { Camera2CameraInfo.from(it).cameraId == lens.cameraId }

    private fun dynamicRangeOf(c: VideoColor): DynamicRange = when (c) {
        VideoColor.SDR -> DynamicRange.SDR
        VideoColor.HLG -> DynamicRange.HLG_10_BIT
        VideoColor.HDR10 -> DynamicRange.HDR10_10_BIT
        VideoColor.HDR10_PLUS -> DynamicRange.HDR10_PLUS_10_BIT
        VideoColor.DOLBY_VISION -> DynamicRange.DOLBY_VISION_10_BIT
    }

    private fun qualityOf(r: VideoRes): Quality = when (r) {
        VideoRes.UHD -> Quality.UHD
        VideoRes.FHD -> Quality.FHD
        VideoRes.HD -> Quality.HD
        VideoRes.SD -> Quality.SD
    }

    /** Checks which of RAW / HDR / video modes this lens supports. */
    fun capabilities(lens: LensInfo): LensCaps {
        val info = cameraInfoFor(lens) ?: return LensCaps()
        val formats = runCatching { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats }
            .getOrDefault(emptySet())
        val hdrExt = runCatching { extensions?.isExtensionAvailable(selectorFor(lens), ExtensionMode.HDR) == true }.getOrDefault(false)
        val nightExt = runCatching { extensions?.isExtensionAvailable(selectorFor(lens), ExtensionMode.NIGHT) == true }.getOrDefault(false)
        val bokehExt = runCatching { extensions?.isExtensionAvailable(selectorFor(lens), ExtensionMode.BOKEH) == true }.getOrDefault(false)
        val faceExt = runCatching { extensions?.isExtensionAvailable(selectorFor(lens), ExtensionMode.FACE_RETOUCH) == true }.getOrDefault(false)
        val videoCaps = runCatching { Recorder.getVideoCapabilities(info) }.getOrNull()
        val ranges = videoCaps?.supportedDynamicRanges ?: emptySet()
        val colors = VideoColor.entries.filter { it == VideoColor.SDR || dynamicRangeOf(it) in ranges }
        val qualities = runCatching { videoCaps?.getSupportedQualities(DynamicRange.SDR) }.getOrNull() ?: emptyList()
        val resolutions = VideoRes.entries.filter { qualityOf(it) in qualities }
        return LensCaps(
            rawJpeg = ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats,
            rawOnly = ImageCapture.OUTPUT_FORMAT_RAW in formats,
            ultraHdrPhoto = ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in formats,
            hdrExtension = hdrExt,
            nightExtension = nightExt,
            bokehExtension = bokehExt,
            faceRetouchExtension = faceExt,
            videoColors = colors,
            videoResolutions = resolutions.ifEmpty { VideoRes.entries },
        )
    }

    /** Reads ISO / shutter / focus / white-balance limits and the available photo sizes and frame rates. */
    fun readSensorInfo(lens: LensInfo): SensorInfo {
        val info = cameraInfoFor(lens) ?: return SensorInfo()
        return try {
            val c2 = Camera2CameraInfo.from(info)
            val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val map: StreamConfigurationMap? = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = buildList {
                map?.getOutputSizes(ImageFormat.JPEG)?.let { addAll(it) }
                map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { addAll(it) }
            }.distinct().sortedByDescending { it.width.toLong() * it.height }

            fun byRatio(r: Float) = sizes.filter { abs(it.width.toFloat() / it.height - r) < 0.03f }
                .distinctBy { (it.width.toLong() * it.height / 500_000) } // one entry per ~0.5 MP step
                .take(8)

            val fps = info.supportedFrameRateRanges
                .flatMap { listOf(it.upper) }
                .filter { it in listOf(24, 25, 30, 48, 50, 60, 120, 240) }
                .distinct().sorted()
            SensorInfo(
                isoRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocusDiopters = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                manualSensor = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps,
                manualWb = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps,
                awbModes = (c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)).toSet(),
                photoSizes43 = byRatio(4f / 3f),
                photoSizes169 = byRatio(16f / 9f),
                fpsOptions = fps.ifEmpty { listOf(30) },
            )
        } catch (e: Exception) {
            SensorInfo()
        }
    }

    // ---------------- binding ----------------

    private fun newPreview(aspect: Int): Preview {
        val builder = Preview.Builder().setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(aspect, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build(),
        )
        Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
        val p = builder.build()
        p.setSurfaceProvider(surfaceProvider)
        preview = p
        return p
    }

    private fun newAnalysis(aspect: Int): ImageAnalysis {
        val a = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy(aspect, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            .build()
        a.setAnalyzer(analysisExecutor, analyzer)
        return a
    }

    /** Binds the lens for photos with the requested format, size, aspect ratio and HDR. */
    fun bindPhoto(owner: LifecycleOwner, lens: LensInfo, spec: PhotoSpec): BindResult {
        val p = provider ?: return BindResult(false, CaptureMode.PHOTO)
        sensorInfo = readSensorInfo(lens)
        val caps = capabilities(lens)
        val notices = mutableListOf<String>()

        val format = when (spec.format) {
            PhotoFormat.RAW_JPEG -> if (caps.rawJpeg) PhotoFormat.RAW_JPEG else {
                notices += "RAW isn't available on this lens"; PhotoFormat.JPEG
            }
            PhotoFormat.RAW -> when {
                caps.rawOnly -> PhotoFormat.RAW
                caps.rawJpeg -> PhotoFormat.RAW_JPEG
                else -> { notices += "RAW isn't available on this lens"; PhotoFormat.JPEG }
            }
            PhotoFormat.JPEG -> PhotoFormat.JPEG
        }
        val isRaw = format != PhotoFormat.JPEG

        var selector = selectorFor(lens)
        var outputFormat = when (format) {
            PhotoFormat.RAW_JPEG -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            PhotoFormat.RAW -> ImageCapture.OUTPUT_FORMAT_RAW
            PhotoFormat.JPEG -> ImageCapture.OUTPUT_FORMAT_JPEG
        }
        var hdrLabel: String? = null
        if (spec.extension != com.geostamp.camera.data.ExtensionModeType.NONE && !isRaw) {
            when (spec.extension) {
                com.geostamp.camera.data.ExtensionModeType.NIGHT -> {
                    if (caps.nightExtension) {
                        selector = extensions!!.getExtensionEnabledCameraSelector(selector, ExtensionMode.NIGHT)
                        hdrLabel = "Night"
                    } else notices += "Night mode not supported on this lens"
                }
                com.geostamp.camera.data.ExtensionModeType.BOKEH -> {
                    if (caps.bokehExtension) {
                        selector = extensions!!.getExtensionEnabledCameraSelector(selector, ExtensionMode.BOKEH)
                        hdrLabel = "Portrait"
                    } else notices += "Portrait blur not supported on this lens"
                }
                com.geostamp.camera.data.ExtensionModeType.FACE_RETOUCH -> {
                    if (caps.faceRetouchExtension) {
                        selector = extensions!!.getExtensionEnabledCameraSelector(selector, ExtensionMode.FACE_RETOUCH)
                        hdrLabel = "Retouch"
                    } else notices += "Face retouch not supported on this lens"
                }
                com.geostamp.camera.data.ExtensionModeType.HDR -> {
                    if (caps.hdrExtension) {
                        selector = extensions!!.getExtensionEnabledCameraSelector(selector, ExtensionMode.HDR)
                        hdrLabel = "HDR"
                    } else if (caps.ultraHdrPhoto) {
                        outputFormat = ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
                        hdrLabel = "Ultra HDR"
                    } else notices += "HDR photos not supported on this lens"
                }
                com.geostamp.camera.data.ExtensionModeType.NONE -> Unit
            }
        } else if (spec.hdr) {
            when {
                isRaw -> notices += "HDR is off while shooting RAW"
                caps.hdrExtension -> {
                    selector = extensions!!.getExtensionEnabledCameraSelector(selector, ExtensionMode.HDR)
                    hdrLabel = "HDR"
                }
                caps.ultraHdrPhoto -> {
                    outputFormat = ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
                    hdrLabel = "Ultra HDR"
                }
                else -> notices += "HDR photos aren't supported on this lens"
            }
        }

        val aspect = if (spec.aspect.native16x9) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        val nativeRatio = if (aspect == AspectRatio.RATIO_16_9) 16f / 9f else 4f / 3f

        fun captureSelector(): ResolutionSelector {
            val b = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(aspect, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            val size = parseSize(spec.resolution)
            b.setResolutionStrategy(
                if (size == null) ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                else ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            )
            return b.build()
        }

        fun attempt(sel: CameraSelector, fmt: Int, withAnalysis: Boolean, sized: Boolean): Boolean {
            val builder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(targetRotation)
                .setFlashMode(flashMode)
                .setOutputFormat(fmt)
            if (sized) builder.setResolutionSelector(captureSelector())
            val capture = builder.build()
            val useCases = mutableListOf(newPreview(aspect), capture)
            if (withAnalysis) useCases += newAnalysis(aspect)
            return try {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, sel, *useCases.toTypedArray())
                imageCapture = capture
                videoCapture = null
                true
            } catch (e: Exception) {
                camera = null
                imageCapture = null
                false
            }
        }

        closeEffect()
        _histogram.value = null
        _focusPeakingBitmap.value = null
        _exposureVerificationBitmap.value = null
        isFocusPeakingEnabled = spec.focusPeaking
        isExposureVerificationEnabled = spec.exposureVerification
        val needsAnalysis = spec.histogram || spec.focusPeaking || spec.exposureVerification

        val notice = notices.joinToString(". ").ifBlank { null }
        if (attempt(selector, outputFormat, needsAnalysis, true)) {
            return BindResult(true, CaptureMode.PHOTO, hdrLabel, format, nativeRatio = nativeRatio, notice = notice)
        }
        if (needsAnalysis && attempt(selector, outputFormat, false, true)) {
            return BindResult(true, CaptureMode.PHOTO, hdrLabel, format, nativeRatio = nativeRatio,
                notice = listOfNotNull(notice, "Display parameters aren't available in this mode").joinToString(". "))
        }
        // Fall back to a plain JPEG camera if the special mode was refused.
        val ok = attempt(selectorFor(lens), ImageCapture.OUTPUT_FORMAT_JPEG, false, false)
        return BindResult(ok, CaptureMode.PHOTO, nativeRatio = nativeRatio,
            notice = if (ok) "The selected HDR/RAW/size couldn't start on this lens; using standard JPEG" else "Couldn't open ${lens.shortLabel}")
    }

    /** Binds the lens for video, burning the stamp into every frame. */
    fun bindVideo(owner: LifecycleOwner, lens: LensInfo, spec: VideoSpec): BindResult {
        val p = provider ?: return BindResult(false, CaptureMode.VIDEO)
        sensorInfo = readSensorInfo(lens)
        val caps = capabilities(lens)
        val notices = mutableListOf<String>()
        val color = if (spec.color in caps.videoColors) spec.color else {
            if (spec.color != VideoColor.SDR) notices += "${spec.color.label} isn't supported on this lens"
            VideoColor.SDR
        }
        val aspect = if (spec.aspect == VideoAspect.R4_3) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9
        val nativeRatio = spec.aspect.ratio
        val info = cameraInfoFor(lens)
        val fpsRange: Range<Int>? = info?.supportedFrameRateRanges?.let { ranges ->
            if (spec.autoFrameRate) {
                ranges.firstOrNull { it.lower < it.upper && it.upper == spec.fps }
                    ?: ranges.firstOrNull { it.upper == spec.fps }
            } else {
                ranges.firstOrNull { it.lower == spec.fps && it.upper == spec.fps }
                    ?: ranges.firstOrNull { it.upper == spec.fps }
            }
        }
        if (fpsRange == null) notices += "${spec.fps} fps isn't supported; using the camera default"

        val stillCapture = ImageCapture.Builder()
            .setTargetRotation(targetRotation)
            .build()

        fun attempt(c: VideoColor, withStamp: Boolean, withAnalysis: Boolean): Boolean {
            val quality = qualityOf(spec.res)
            val recorderBuilder = Recorder.Builder()
                .setAspectRatio(aspect)
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality)))
            if (spec.bitrate != Bitrate.AUTO) {
                recorderBuilder.setTargetVideoEncodingBitRate((spec.res.baseMbps * spec.bitrate.factor * (if (c != VideoColor.SDR) 1.25f else 1f) * 1_000_000).toInt())
            }
            val builder = VideoCapture.Builder(recorderBuilder.build())
                .setTargetRotation(targetRotation)
                .setVideoStabilizationEnabled(spec.stabilization)
            if (fpsRange != null) builder.setTargetFrameRate(fpsRange)
            if (c != VideoColor.SDR) builder.setDynamicRange(dynamicRangeOf(c))
            val vc = builder.build()
            closeEffect()
            val group = UseCaseGroup.Builder().addUseCase(newPreview(aspect)).addUseCase(vc)
            // Attempt to bind concurrent ImageCapture for snapshots while recording
            try { group.addUseCase(stillCapture) } catch (_: Exception) {}
            if (withAnalysis) group.addUseCase(newAnalysis(aspect))
            if (withStamp) {
                val effect = OverlayEffect(CameraEffect.VIDEO_CAPTURE, 0, overlayHandler) { }
                effect.setOnDrawListener { frame -> drawVideoStamp(frame) }
                group.addEffect(effect)
                overlayEffect = effect
            }
            return try {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, selectorFor(lens), group.build())
                videoCapture = vc
                imageCapture = stillCapture
                true
            } catch (e: Exception) {
                // If concurrent binding failed, fallback to video + preview only
                try {
                    val fallbackGroup = UseCaseGroup.Builder().addUseCase(newPreview(aspect)).addUseCase(vc)
                    if (withAnalysis) fallbackGroup.addUseCase(newAnalysis(aspect))
                    if (withStamp && overlayEffect != null) fallbackGroup.addEffect(overlayEffect!!)
                    camera = p.bindToLifecycle(owner, selectorFor(lens), fallbackGroup.build())
                    videoCapture = vc
                    imageCapture = null
                    true
                } catch (_: Exception) {
                    camera = null
                    videoCapture = null
                    imageCapture = null
                    closeEffect()
                    false
                }
            }
        }

        _histogram.value = null
        _focusPeakingBitmap.value = null
        _exposureVerificationBitmap.value = null
        isFocusPeakingEnabled = spec.focusPeaking
        isExposureVerificationEnabled = spec.exposureVerification
        val needsAnalysis = spec.histogram || spec.focusPeaking || spec.exposureVerification

        val hdrLabel = if (color != VideoColor.SDR) color.label.substringBefore(" 10") else null
        fun result(c: VideoColor, stamp: Boolean, extra: String? = null) = BindResult(
            true, CaptureMode.VIDEO,
            hdrLabel = if (c != VideoColor.SDR) c.label.substringBefore(" 10") else null,
            stampOnVideo = stamp, color = c, nativeRatio = nativeRatio,
            notice = (notices + listOfNotNull(extra)).joinToString(". ").ifBlank { null },
        )

        if (attempt(color, true, needsAnalysis)) return result(color, true)
        if (needsAnalysis && attempt(color, true, false)) return result(color, true, "Display parameters aren't available with these video settings")
        if (color != VideoColor.SDR && attempt(VideoColor.SDR, true, false)) {
            return result(VideoColor.SDR, true, "This phone can't put the stamp on ${color.label} video, so it records in SDR")
        }
        if (attempt(color, false, false)) {
            return result(color, false, "This phone can't burn the stamp into this video mode; recording without it")
        }
        if (hdrLabel != null && attempt(VideoColor.SDR, false, false)) {
            return result(VideoColor.SDR, false, "Video started in basic mode (no stamp, SDR)")
        }
        return BindResult(false, CaptureMode.VIDEO, notice = "Couldn't start video on ${lens.shortLabel}")
    }

    private fun parseSize(s: String): Size? {
        val parts = s.split("x")
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return Size(w, h)
    }

    /** Pushes the manual ISO / shutter / WB / focus settings to the camera. */
    fun applyManual(state: ManualState, fps: Int) {
        val cam = camera ?: return
        try {
            Camera2CameraControl.from(cam.cameraControl)
                .setCaptureRequestOptions(Manual.options(state, sensorInfo, _readout.value, fps))
            if (!state.exposureManual) {
                val es = cam.cameraInfo.exposureState
                if (es.isExposureCompensationSupported) {
                    val r = es.exposureCompensationRange
                    cam.cameraControl.setExposureCompensationIndex(state.ev.coerceIn(r.lower, r.upper))
                }
            }
        } catch (e: Exception) {
            // Some modes (e.g. HDR extension) don't accept manual options.
        }
    }

    /** The resolution actually chosen by the camera, e.g. "4000x3000". */
    fun currentResolution(): Size? =
        (imageCapture?.resolutionInfo ?: videoCapture?.resolutionInfo)?.resolution

    fun unbind() {
        provider?.unbindAll()
        camera = null
        imageCapture = null
        videoCapture = null
        closeEffect()
    }

    private fun closeEffect() {
        overlayEffect?.let { runCatching { it.close() } }
        overlayEffect = null
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun focusAt(point: MeteringPoint) {
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    var isTrackingFocus = false
    fun startFocusTracking(point: MeteringPoint) {
        isTrackingFocus = true
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun stopFocusTracking() {
        isTrackingFocus = false
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    // ---------------- photos ----------------

    /** Normal or HDR photo, delivered in memory for stamping. */
    suspend fun takePhoto(): CapturedPhoto {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not ready")
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        buffer.rewind()
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        cont.resume(CapturedPhoto(bytes, image.imageInfo.rotationDegrees))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    private fun rawOutputOptions(location: Location?): ImageCapture.OutputFileOptions {
        val name = "GeoStamp_RAW_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US).format(ZonedDateTime.now())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.dng")
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeoStamp")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GeoStamp")
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, "$name.dng").absolutePath)
            }
        }
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ).setMetadata(metadata).build()
    }

    /**
     * RAW + JPEG capture. The untouched RAW (.dng) goes straight to Pictures/GeoStamp;
     * the JPEG is written to [jpegFile] so it can be stamped. Returns the DNG's Uri.
     */
    suspend fun takeRawAndJpeg(jpegFile: File, location: Location?): Uri? {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not ready")
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        val jpegOptions = ImageCapture.OutputFileOptions.Builder(jpegFile).setMetadata(metadata).build()
        val rawOptions = rawOutputOptions(location)

        return suspendCancellableCoroutine { cont ->
            var rawUri: Uri? = null
            var results = 0
            var finished = false
            capture.takePicture(rawOptions, jpegOptions, captureExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Called once for the RAW file (saved to the gallery) and once for the JPEG (temp file).
                    val uri = output.savedUri
                    if (output.imageFormat == ImageFormat.RAW_SENSOR || uri?.scheme == "content") rawUri = uri
                    results++
                    if (results == 2 && !finished) {
                        finished = true
                        cont.resume(rawUri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (!finished) {
                        finished = true
                        cont.resumeWithException(exception)
                    }
                }
            })
        }
    }

    /** RAW-only capture: an untouched DNG (a stamp can't be burned into RAW sensor data). */
    suspend fun takeRawOnly(location: Location?): Uri? {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not ready")
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(rawOutputOptions(location), captureExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(output.savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    // ---------------- video ----------------

    private val overlayThread = HandlerThread("stamp-overlay").apply { start() }
    private val overlayHandler = Handler(overlayThread.looper)
    private val stampPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    @Volatile private var videoStamp: Bitmap? = null
    @Volatile private var videoStampSize: StampSize = StampSize.MEDIUM
    @Volatile private var videoStampTop: Boolean = false

    /** Stamp to burn into the video (updated every second by the camera screen). */
    fun setVideoStamp(stamp: Bitmap?, size: StampSize, top: Boolean) {
        videoStamp = stamp
        videoStampSize = size
        videoStampTop = top
    }

    private fun drawVideoStamp(frame: Frame): Boolean {
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val stamp = videoStamp ?: return true
        val crop = frame.cropRect
        val rotation = frame.rotationDegrees
        // Size of the video as it will be seen (upright).
        val uw = if (rotation % 180 == 0) crop.width() else crop.height()
        val uh = if (rotation % 180 == 0) crop.height() else crop.width()
        if (uw <= 0 || uh <= 0) return true

        canvas.save()
        canvas.translate(crop.exactCenterX(), crop.exactCenterY())
        if (frame.isMirroring) canvas.scale(-1f, 1f)
        canvas.rotate(-rotation.toFloat())
        canvas.translate(-uw / 2f, -uh / 2f)

        val sw = PhotoComposer.stampWidthFor(uw, uh, videoStampSize).toFloat()
        val sh = stamp.height * sw / stamp.width
        val margin = PhotoComposer.marginFor(uw)
        val x = (uw - sw) / 2f
        val y = if (videoStampTop) margin else uh - sh - margin
        canvas.drawBitmap(stamp, null, RectF(x, y, x + sw, y + sh), stampPaint)
        canvas.restore()
        return true
    }

    val isVideoReady: Boolean get() = videoCapture != null

    @SuppressLint("MissingPermission")
    fun startRecording(location: Location?, withAudio: Boolean, onEvent: (VideoRecordEvent) -> Unit): Recording? {
        val vc = videoCapture ?: return null
        val name = "GeoStamp_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GeoStamp")
            }
        }
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .setLocation(location)
            .build()
        var pending = vc.output.prepareRecording(context, options)
        if (withAudio) pending = pending.withAudioEnabled()
        return pending.start(ContextCompat.getMainExecutor(context)) { event -> onEvent(event) }
    }

    fun shutdown() {
        unbind()
        captureExecutor.shutdown()
        analysisExecutor.shutdown()
        overlayThread.quitSafely()
    }
}
