package com.geostamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stamps and saves photos after the shutter has been released, so the camera is ready for the
 * next shot while the previous one is still being decoded, stamped, signed and written.
 *
 * Saves run one at a time (each holds a full-size bitmap) on an app-wide scope, so a photo still
 * gets saved if the user leaves the camera screen before it's done.
 */
object BackgroundSaves {
    /** Shots that may wait in the queue before the shutter waits for one to finish. */
    const val MAX_QUEUED = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val oneAtATime = Mutex()
    private val _pending = MutableStateFlow(0)

    /** Saves queued or running. */
    val pending: StateFlow<Int> = _pending

    fun enqueue(save: suspend () -> Unit) {
        _pending.update { it + 1 }
        scope.launch {
            try {
                oneAtATime.withLock { save() }
            } finally {
                _pending.update { it - 1 }
            }
        }
    }

    /** Suspends while the queue is full, so a long burst can't run the phone out of memory. */
    suspend fun awaitRoom() {
        _pending.first { it < MAX_QUEUED }
    }

    /**
     * A small upright preview of a camera JPEG, decoded at reduced size so it's ready in a few
     * milliseconds; shown as the last-capture thumbnail until the stamped photo is saved.
     */
    fun quickThumbnail(jpeg: ByteArray, rotationDegrees: Int, targetWidth: Int = 240): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetWidth) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        if (rotationDegrees % 360 == 0) return bmp
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }
}
