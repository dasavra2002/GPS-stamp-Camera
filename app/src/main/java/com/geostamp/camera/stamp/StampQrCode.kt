package com.geostamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The QR code printed on the stamp: a readable summary of where and when the photo was taken.
 *
 * It is not proof by itself (anyone can print a QR code). The proof is the signature
 * stored in the photo's metadata, see [CaptureProof]; the app's Verify screen checks it.
 */
object StampQrCode {

    /** Readable text for the QR code: map link, coordinates, time and photo number. */
    fun buildPayload(
        lat: Double?,
        lon: Double?,
        alt: Double?,
        time: ZonedDateTime,
        plusCode: String?,
        number: Int,
    ): String {
        val isoTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US).format(time)
        return buildString {
            if (lat != null && lon != null) {
                append(String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f\n", lat, lon))
                append(String.format(Locale.US, "LAT: %.6f, LON: %.6f\n", lat, lon))
            }
            if (plusCode != null) append("CODE: ").append(plusCode).append('\n')
            if (alt != null) append(String.format(Locale.US, "ALT: %.0fm\n", alt))
            append("TIME: ").append(isoTime).append('\n')
            append("PHOTO #").append(number).append('\n')
            append("VERIFY: GeoStamp app > Gallery > Verify photo")
        }
    }

    /** Generates a black/white QR code bitmap. */
    fun generateQrBitmap(content: String, sizePx: Int): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
