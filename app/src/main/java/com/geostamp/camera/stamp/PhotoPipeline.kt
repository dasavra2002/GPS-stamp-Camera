package com.geostamp.camera.stamp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSize
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object PhotoComposer {
    /** Photos larger than this are decoded at half size to avoid running out of memory. */
    private const val MAX_PIXELS = 26_000_000L

    /** Width of the stamp for a photo, so preview and saved photo look identical. */
    fun stampWidthFor(photoW: Int, photoH: Int, size: StampSize): Int =
        (min(photoW * 0.96f, photoH * 1.25f) * size.scale / StampSize.LARGE.scale).roundToInt()

    fun marginFor(photoW: Int): Float = photoW * 0.02f

    fun compose(photo: Bitmap, config: StampConfig, data: StampData, time: ZonedDateTime, cameraLabel: String?): Bitmap {
        val out = if (photo.isMutable) photo else photo.copy(Bitmap.Config.ARGB_8888, true)
        val sw = stampWidthFor(out.width, out.height, config.size)
        val stamp = WatermarkRenderer.render(sw, config, data, time, cameraLabel) ?: return out
        val margin = marginFor(out.width)
        val x = (out.width - sw) / 2f
        val y = if (config.position == StampPosition.BOTTOM) out.height - stamp.height - margin else margin
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

    /**
     * Centre-crops a photo to [ratio] (long side / short side), keeping its orientation.
     * Used for the 3:2, 1:1, 5:4 and 2.39:1 photo formats.
     */
    fun cropToAspect(src: Bitmap, ratio: Float): Bitmap {
        val landscape = src.width >= src.height
        val want = if (landscape) ratio else 1f / ratio // width / height
        val current = src.width.toFloat() / src.height
        val w: Int
        val h: Int
        if (current > want) {
            h = src.height; w = (src.height * want).roundToInt().coerceIn(1, src.width)
        } else {
            w = src.width; h = (src.width / want).roundToInt().coerceIn(1, src.height)
        }
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
    fun save(context: Context, bitmap: Bitmap, data: StampData, time: ZonedDateTime, prefix: String = "GeoStamp"): Uri? {
        val name = prefix + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US).format(ZonedDateTime.now()) + ".jpg"
        val temp = File(context.cacheDir, name)
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        writeExif(temp, data, time)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveQ(context, temp, name) else saveLegacy(context, temp, name)
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

    private fun saveQ(context: Context, temp: File, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
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
    private fun saveLegacy(context: Context, temp: File, name: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
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
