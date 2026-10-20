package com.geostamp.camera

import com.geostamp.camera.stamp.BarcodeScanner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeScannerTest {

    @Test
    fun testQrCodeEncodeAndDecode() {
        val testPayload = "ASSET-TRANFORMER-99812"
        val size = 200
        val bitMatrix = QRCodeWriter().encode(testPayload, BarcodeFormat.QR_CODE, size, size)

        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val decoded = BarcodeScanner.scanPixels(pixels, size, size)
        assertNotNull(decoded)
        assertEquals(testPayload, decoded)
    }

    @Test
    fun testEmptyOrNoiseFailsGracefully() {
        val size = 100
        val blankPixels = IntArray(size * size) { 0xFFFFFFFF.toInt() }
        val decoded = BarcodeScanner.scanPixels(blankPixels, size, size)
        assertNull(decoded)
    }
}
