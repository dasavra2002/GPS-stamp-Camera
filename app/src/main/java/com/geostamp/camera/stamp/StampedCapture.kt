package com.geostamp.camera.stamp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import java.time.ZonedDateTime

/**
 * Turns one camera frame into a saved, stamped, signed photo: crop, stamp, carry over the
 * camera EXIF, optionally keep the clean original, save, and make a thumbnail.
 * Shared by single shots, bursts and timelapse frames so they can't drift apart.
 */
object StampedCapture {
    private const val THUMB_W = 240

    class Request(
        val frame: Bitmap,
        val config: StampConfig,
        val data: StampData,
        val time: ZonedDateTime,
        val lensLabel: String?,
        val shotParams: ShotParams?,
        /** Long-side / short-side ratio to centre-crop to, or null to keep the full frame. */
        val cropRatio: Float?,
        val prefix: String = "GeoStamp",
        val subFolder: String? = null,
        /** The camera's own JPEG: its EXIF is carried over, and it's saved as the original if [keepOriginal]. */
        val cameraJpeg: ByteArray? = null,
        /** CameraX's rotation for an in-memory JPEG; null when the file's EXIF already has it. */
        val cameraRotation: Int? = null,
        val keepOriginal: Boolean = false,
    )

    class Result(val uri: Uri?, val thumbnail: Bitmap)

    /** Blocking; call off the main thread. Recycles [Request.frame]. */
    fun save(context: Context, r: Request): Result {
        val source = r.data.locationSource ?: "none"
        val exif = CameraExif.fromShotParams(r.shotParams) + (r.cameraJpeg?.let { CameraExif.read(it) } ?: emptyMap())
        if (r.keepOriginal && r.cameraJpeg != null) {
            PhotoSaver.saveOriginal(context, r.cameraJpeg, r.data, r.time, r.config.number, source, r.subFolder, r.cameraRotation)
        }
        val cropped = r.cropRatio?.let { PhotoComposer.cropToAspect(r.frame, it) } ?: r.frame
        val stamped = PhotoComposer.compose(cropped, r.config, r.data, r.time, r.lensLabel, r.shotParams)
        val uri = PhotoSaver.save(
            context, stamped, r.data, r.time, r.config.number, source,
            prefix = r.prefix, subFolder = r.subFolder, cameraExif = exif,
        )
        val thumb = Bitmap.createScaledBitmap(stamped, THUMB_W, (stamped.height * THUMB_W / stamped.width).coerceAtLeast(1), true)
        stamped.recycle()
        if (cropped !== stamped && !cropped.isRecycled) cropped.recycle()
        if (r.frame !== cropped && r.frame !== stamped && !r.frame.isRecycled) r.frame.recycle()
        return Result(uri, thumb)
    }
}
