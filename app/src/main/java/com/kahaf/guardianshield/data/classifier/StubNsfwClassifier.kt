package com.kahaf.guardianshield.data.classifier

import android.graphics.Bitmap
import com.kahaf.guardianshield.domain.model.NsfwLabel
import com.kahaf.guardianshield.domain.model.NsfwResult
import com.kahaf.guardianshield.domain.repository.NsfwClassifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic SAFE classifier. Used when no real model is bundled or as a
 * test double. This keeps the build green even without `nsfw_v1.tflite`.
 */
@Singleton
class StubNsfwClassifier @Inject constructor() : NsfwClassifier {
    override suspend fun classify(bitmap: Bitmap): NsfwResult {
        return NsfwResult(
            label = NsfwLabel.SAFE,
            confidence = 1f,
            scores = mapOf(
                NsfwLabel.SAFE to 1f,
                NsfwLabel.NATURAL to 0f,
                NsfwLabel.SUGGESTIVE to 0f,
                NsfwLabel.EXPLICIT to 0f
            )
        )
    }

    override fun close() = Unit
}
