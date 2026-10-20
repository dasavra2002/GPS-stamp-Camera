package com.geostamp.camera

import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.stamp.PhotoComposer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class AspectCroppingTest {

    @Test
    fun testSquareCropLandscape() {
        // 4000x3000 landscape to 1:1
        val (w, h) = PhotoComposer.calculateCropDimensions(4000, 3000, 1.0f)
        assertEquals(3000, w)
        assertEquals(3000, h)
    }

    @Test
    fun testSquareCropPortrait() {
        // 3000x4000 portrait to 1:1
        val (w, h) = PhotoComposer.calculateCropDimensions(3000, 4000, 1.0f)
        assertEquals(3000, w)
        assertEquals(3000, h)
    }

    @Test
    fun test3x2CropLandscape() {
        // 4000x3000 (4:3 = 1.333) to 3:2 (1.5)
        // Ratio wanted is 1.5, which is wider than 1.333, so height must be cropped
        val (w, h) = PhotoComposer.calculateCropDimensions(4000, 3000, 1.5f)
        assertEquals(4000, w)
        assertEquals(2667, h) // 4000 / 1.5 = 2666.666 -> roundToInt = 2667
        val resultingRatio = w.toFloat() / h
        assertEquals(1.5f, resultingRatio, 0.01f)
    }

    @Test
    fun test3x2CropPortrait() {
        // 3000x4000 (3:4 = 0.75) to 2:3 (0.6667)
        val (w, h) = PhotoComposer.calculateCropDimensions(3000, 4000, 1.5f)
        assertEquals(2667, w)
        assertEquals(4000, h)
        val resultingRatio = h.toFloat() / w
        assertEquals(1.5f, resultingRatio, 0.01f)
    }

    @Test
    fun testAllPhotoAspectEnums() {
        val baseW = 4000
        val baseH = 3000 // 4:3 native sensor
        for (aspect in PhotoAspect.entries) {
            val (w, h) = PhotoComposer.calculateCropDimensions(baseW, baseH, aspect.ratio)
            val ratio = w.toFloat() / h
            assertEquals("Ratio for ${aspect.label} must match", aspect.ratio, ratio, 0.015f)
        }
    }

    @Test
    fun testNoOpWhenRatioAlreadyMatches() {
        // 4000x3000 has ratio 4/3. If ratio requested is 4/3, dimensions shouldn't change.
        val (w, h) = PhotoComposer.calculateCropDimensions(4000, 3000, 4f / 3f)
        assertEquals(4000, w)
        assertEquals(3000, h)
    }
}
