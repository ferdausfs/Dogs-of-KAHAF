package com.kahaf.guardianshield.domain.usecase

import android.graphics.Bitmap
import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.model.NsfwLabel
import com.kahaf.guardianshield.domain.model.NsfwResult
import com.kahaf.guardianshield.domain.repository.NsfwClassifier
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frame analysis with debounce. Caller passes a series of bitmaps; we only return
 * `confirmed = true` once N consecutive EXPLICIT frames have arrived inside the
 * configured window.
 *
 * The debounce buffer is stored per-package so multiple foreground apps do not
 * leak state into each other.
 */
@Singleton
class AnalyzeFrameUseCase @Inject constructor(
    private val classifier: NsfwClassifier,
    private val settingsRepository: SettingsRepository
) {
    private data class FrameStamp(val ts: Long, val confidence: Float)

    private val buffers = HashMap<String, ArrayDeque<FrameStamp>>()

    data class Outcome(
        val result: NsfwResult,
        val confirmed: Boolean
    )

    suspend fun analyze(packageName: String, bitmap: Bitmap): Outcome {
        val ai: AiSettings = settingsRepository.aiSettings.first()
        val res = classifier.classify(bitmap)
        val threshold = ai.thresholdFor(packageName)
        val explicitConfidence = res.scores[NsfwLabel.EXPLICIT] ?: 0f

        // v3.1.3 FIX: previously a frame had to satisfy BOTH `label == EXPLICIT`
        // (which for 2-class models hard-cut at nsfw≥0.80) AND a threshold check
        // — making the user's sensitivity slider effectively a no-op above ~0.20
        // because the label gate fired before the threshold ever did. The new
        // logic: the score itself crossing the user-set threshold is enough,
        // OR the model already decided EXPLICIT. This wires the slider back to
        // real behaviour and lets stricter sensitivity catch SUGGESTIVE-tier
        // frames the user explicitly opted-in to.
        val crossedThreshold = explicitConfidence >= threshold
        val labelExplicit    = res.label == NsfwLabel.EXPLICIT
        val isExplicit       = labelExplicit || crossedThreshold

        val now = System.currentTimeMillis()
        val buf = buffers.getOrPut(packageName) { ArrayDeque() }
        if (isExplicit) {
            buf.addLast(FrameStamp(now, explicitConfidence))
        }
        // drop stale frames
        while (buf.isNotEmpty() && (now - buf.first().ts) > ai.debounceWindowMs) {
            buf.removeFirst()
        }
        val confirmed = isExplicit && buf.size >= ai.debounceFrames
        if (confirmed) buf.clear()
        return Outcome(result = res, confirmed = confirmed)
    }

    fun reset(packageName: String) { buffers.remove(packageName) }
}
