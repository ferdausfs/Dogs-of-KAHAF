package com.kahaf.guardianshield.domain.repository

import android.graphics.Bitmap
import com.kahaf.guardianshield.domain.model.NsfwResult

/**
 * Abstract on-device NSFW classifier. Lives in the domain layer so the rest of
 * the app never depends on TFLite directly. Implementations:
 *  - StubNsfwClassifier (always SAFE)            — keeps the build green
 *  - TfLiteNsfwClassifier (uses assets/nsfw_v1.tflite, NNAPI when available)
 */
interface NsfwClassifier {
    suspend fun classify(bitmap: Bitmap): NsfwResult
    fun close()
}
