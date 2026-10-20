package com.geostamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TamperProofSigner {

    private const val APP_SECRET = "GeoStampCamera_v1_SecuritySalt"

    /** Computes a compact SHA-256 digital signature of the capture details. */
    fun computeSignature(
        lat: Double?,
        lon: Double?,
        alt: Double?,
        time: ZonedDateTime,
        number: Int,
    ): String {
        val raw = buildString {
            append(lat?.let { String.format(Locale.US, "%.6f", it) } ?: "null").append(':')
            append(lon?.let { String.format(Locale.US, "%.6f", it) } ?: "null").append(':')
            append(alt?.let { String.format(Locale.US, "%.1f", it) } ?: "null").append(':')
            append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time)).append(':')
            append(number).append(':')
            append(APP_SECRET)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12).uppercase(Locale.US)
    }

    /** Formats a compact, scannable verification text containing the location, time, and signature. */
    fun buildPayload(
        lat: Double?,
        lon: Double?,
        alt: Double?,
        time: ZonedDateTime,
        plusCode: String?,
        number: Int,
    ): String {
        val sig = computeSignature(lat, lon, alt, time, number)
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
            append("VERIFIED SIG: ").append(sig)
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
