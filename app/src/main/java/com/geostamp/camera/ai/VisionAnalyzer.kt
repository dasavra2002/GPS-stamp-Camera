package com.geostamp.camera.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device free ML Kit analyzers for:
 * 1. Signboard & meter text reading (OCR)
 * 2. Automatic scene captioning (e.g., "Construction site, Crane, Infrastructure")
 */
object VisionAnalyzer {
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val imageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Reads text from a captured photo or signboard, e.g. asset IDs, meter numbers, or street signs.
     */
    suspend fun recognizeText(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            val text = result.text.trim()
            if (text.isNotBlank()) text else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a descriptive scene caption from image content (e.g. "Building, Construction, Vehicle").
     */
    suspend fun autoCaption(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = imageLabeler.process(image).await()
            val topLabels = labels
                .filter { it.confidence >= 0.60f }
                .map { it.text.replaceFirstChar { c -> c.uppercase() } }
                .distinct()
                .take(4)
            if (topLabels.isNotEmpty()) topLabels.joinToString(", ") else null
        } catch (_: Exception) {
            null
        }
    }
}
