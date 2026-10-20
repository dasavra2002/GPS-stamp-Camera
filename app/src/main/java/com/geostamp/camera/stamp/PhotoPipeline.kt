package com.geostamp.camera.stamp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSize
import com.geostamp.camera.data.WatermarkStyle
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

enum class PipPosition {
    TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT
}

object PhotoComposer {
    /** Photos larger than this are decoded at half size to avoid running out of memory. */
    private const val MAX_PIXELS = 26_000_000L

    /** Width of the stamp for a photo, so preview and saved photo look identical. */
    fun stampWidthFor(photoW: Int, photoH: Int, size: StampSize): Int =
        (min(photoW * 0.96f, photoH * 1.25f) * size.scale / StampSize.LARGE.scale).roundToInt()

    fun marginFor(photoW: Int): Float = photoW * 0.02f

    /**
     * Composites a secondary inset image (e.g. front selfie camera) into the primary photo.
     */
    fun compositePip(
        main: Bitmap,
        inset: Bitmap,
        position: PipPosition = PipPosition.TOP_RIGHT,
        sizeFraction: Float = 0.28f,
    ): Bitmap {
        val out = if (main.isMutable) main else main.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val insetW = (main.width * sizeFraction).roundToInt().coerceAtLeast(100)
        val insetH = (insetW * 4f / 3f).roundToInt()
        val scaledInset = Bitmap.createScaledBitmap(inset, insetW, insetH, true)

        val margin = main.width * 0.03f
        val left = when (position) {
            PipPosition.TOP_LEFT, PipPosition.BOTTOM_LEFT -> margin
            PipPosition.TOP_RIGHT, PipPosition.BOTTOM_RIGHT -> main.width - insetW - margin
        }
        val top = when (position) {
            PipPosition.TOP_LEFT, PipPosition.TOP_RIGHT -> margin
            PipPosition.BOTTOM_LEFT, PipPosition.BOTTOM_RIGHT -> (main.height - insetH - margin - (main.height * 0.20f)).coerceAtLeast(margin)
        }

        val rectF = RectF(left, top, left + insetW, top + insetH)
        val cornerRadius = insetW * 0.12f

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
        canvas.drawRoundRect(RectF(left + 6f, top + 6f, left + insetW + 6f, top + insetH + 6f), cornerRadius, cornerRadius, shadowPaint)

        // Clip and draw rounded inset image
        val clipPath = Path().apply { addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(scaledInset, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        // Amber border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (insetW * 0.025f).coerceAtLeast(4f)
            color = 0xFFFFC107.toInt()
        }
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

        if (scaledInset !== inset && !scaledInset.isRecycled) scaledInset.recycle()
        return out
    }

    /**
     * Enhances paper documents and receipts by boosting text contrast,
     * flattening shadows, and sharpening edges.
     */
    fun applyDocumentEnhancement(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix(floatArrayOf(
            1.4f, 0f, 0f, 0f, -40f,
            0f, 1.4f, 0f, 0f, -40f,
            0f, 0f, 1.4f, 0f, -40f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun compose(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        cameraLabel: String?,
        shotParams: ShotParams? = null,
    ): Bitmap {
        if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
            return FrameWatermarkRenderer.render(photo, config, data, time, shotParams)
        }
        val out = if (photo.isMutable) photo else photo.copy(Bitmap.Config.ARGB_8888, true).also { photo.recycle() }
        val sw = stampWidthFor(out.width, out.height, config.size)
        val stamp = WatermarkRenderer.render(sw, config, data, time, cameraLabel) ?: return out
        val margin = marginFor(out.width)
        val x = (out.width - sw) / 2f
        val y = when (config.position) {
            StampPosition.BOTTOM -> out.height - stamp.height - margin
            StampPosition.TOP -> margin
            StampPosition.CENTER -> (out.height - stamp.height) / 2f
        }
        Canvas(out).drawBitmap(stamp, x, y, Paint(Paint.FILTER_BITMAP_FLAG))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            neutralizeGainmap(out, x, y, sw.toFloat(), stamp.height.toFloat())
        }
        stamp.recycle()
        return out
    }

    /**
     * Ultra HDR photos carry a gain map that brightens highlights on HDR screens.
     * Clearing it under the stamp keeps the stamp at normal brightness.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun neutralizeGainmap(photo: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        try {
            val gainmap = photo.gainmap ?: return
            val contents = gainmap.gainmapContents
            val editable = if (contents.isMutable) contents else contents.copy(contents.config ?: Bitmap.Config.ARGB_8888, true)
            val sx = editable.width.toFloat() / photo.width
            val sy = editable.height.toFloat() / photo.height
            val clear = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
            Canvas(editable).drawRect(x * sx, y * sy, (x + w) * sx, (y + h) * sy, clear)
            gainmap.gainmapContents = editable
            photo.gainmap = gainmap
        } catch (e: Exception) {
            // Not critical: the photo is still saved, only the HDR boost under the stamp remains.
        }
    }

    /** Calculates target dimensions for centre-cropping to [ratio] (long / short side). */
    fun calculateCropDimensions(srcWidth: Int, srcHeight: Int, ratio: Float): Pair<Int, Int> {
        val landscape = srcWidth >= srcHeight
        val want = if (landscape) ratio else 1f / ratio // width / height
        val current = srcWidth.toFloat() / srcHeight
        val w: Int
        val h: Int
        if (current > want) {
            h = srcHeight; w = (srcHeight * want).roundToInt().coerceIn(1, srcWidth)
        } else {
            w = srcWidth; h = (srcWidth / want).roundToInt().coerceIn(1, srcHeight)
        }
        return w to h
    }

    /**
     * Centre-crops a photo to [ratio] (long side / short side), keeping its orientation.
     * Used for the 3:2, 1:1, 5:4 and 2.39:1 photo formats.
     */
    fun cropToAspect(src: Bitmap, ratio: Float): Bitmap {
        val (w, h) = calculateCropDimensions(src.width, src.height, ratio)
        if (w == src.width && h == src.height) return src
        val out = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
        val mutable = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true).also { if (out !== src) out.recycle() }
        if (mutable !== src) src.recycle()
        return mutable
    }

    /** Decodes a JPEG file written by the camera (RAW+JPEG mode), honouring its EXIF rotation. */
    fun decodeFile(context: Context, file: File): Bitmap = decodeUri(context, Uri.fromFile(file))

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w.toLong() / s * (h.toLong() / s) > MAX_PIXELS) s *= 2
        return s
    }

    private fun rotate(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
        if (degrees % 360 == 0 && !mirror) {
            return if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        }
        val m = Matrix()
        if (mirror) m.postScale(-1f, 1f)
        m.postRotate(degrees.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Decodes a JPEG from the camera and turns it upright. */
    fun decodeCameraJpeg(bytes: ByteArray, rotationDegrees: Int, mirror: Boolean = false): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inMutable = true
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("Could not decode photo")
        return rotate(bmp, rotationDegrees, mirror)
    }

    /** Decodes a gallery photo, honouring its EXIF orientation. */
    fun decodeUri(context: Context, uri: Uri): Bitmap {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inMutable = true
        }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("Could not open photo")
        val (deg, mirror) = exifRotation(context, uri)
        return rotate(bmp, deg, mirror)
    }

    /** Rotation (degrees) and mirroring stored in a photo's EXIF orientation tag. */
    private fun exifRotation(context: Context, uri: Uri): Pair<Int, Boolean> {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
            ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
            ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
            ExifInterface.ORIENTATION_TRANSPOSE -> 270 to true
            ExifInterface.ORIENTATION_TRANSVERSE -> 90 to true
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
            else -> 0 to false
        }
    }

    /** Small, upright preview of a gallery photo (for lists). */
    fun thumbnail(context: Context, uri: Uri, maxSide: Int = 320): Bitmap? = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= maxSide && bounds.outHeight / (s * 2) >= maxSide) s *= 2
        val small = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        small?.let {
            val (deg, mirror) = exifRotation(context, uri)
            rotate(it, deg, mirror)
        }
    } catch (e: Exception) {
        null
    }
}

object PhotoSaver {
    private const val FOLDER = "GeoStamp"

    /** Saves the stamped photo to Pictures/GeoStamp with GPS and date EXIF. Returns its Uri. */
    fun save(context: Context, bitmap: Bitmap, data: StampData, time: ZonedDateTime, prefix: String = "GeoStamp", subFolder: String? = null): Uri? {
        val name = prefix + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US).format(ZonedDateTime.now()) + ".jpg"
        val temp = File(context.cacheDir, name)
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        writeExif(temp, data, time)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveQ(context, temp, name, subFolder) else saveLegacy(context, temp, name, subFolder)
        } finally {
            temp.delete()
        }
    }

    private fun writeExif(file: File, d: StampData, time: ZonedDateTime) {
        try {
            val exif = ExifInterface(file)
            if (d.latitude != null && d.longitude != null) exif.setLatLong(d.latitude, d.longitude)
            d.altitude?.let { exif.setAltitude(it) }
            val dt = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US).format(time)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dt)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dt)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, DateTimeFormatter.ofPattern("xxx", Locale.US).format(time))
            d.azimuthDeg?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(it * 100).roundToInt()}/100")
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
            }
            d.address?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoStamp Camera")
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            exif.saveAttributes()
        } catch (e: Exception) {
            // EXIF is a bonus; the stamp is already burned into the pixels.
        }
    }

    private fun saveQ(context: Context, temp: File, name: String, subFolder: String? = null): Uri? {
        val relPath = if (!subFolder.isNullOrBlank()) {
            val safe = subFolder.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            Environment.DIRECTORY_PICTURES + "/" + FOLDER + "/" + safe
        } else {
            Environment.DIRECTORY_PICTURES + "/" + FOLDER
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            cr.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, temp: File, name: String, subFolder: String? = null): Uri? {
        val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        val dir = if (!subFolder.isNullOrBlank()) {
            val safe = subFolder.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            File(baseDir, safe)
        } else baseDir
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, name)
        temp.copyTo(dest, overwrite = true)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, dest.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
