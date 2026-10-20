package com.geostamp.camera.stamp

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * On-device QR and barcode scanner using ZXing.
 * Supports QR codes, Code 128, Code 39, EAN-13, DataMatrix, UPC, etc.
 */
object BarcodeScanner {
    fun scan(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return scanPixels(pixels, width, height)
    }

    fun scanPixels(pixels: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val reader = MultiFormatReader()
            val result = reader.decodeWithState(binaryBitmap)
            result.text
        } catch (_: Exception) {
            null
        }
    }
}
