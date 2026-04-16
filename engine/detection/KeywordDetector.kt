package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.util.Constants
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordDetector @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val keywords: List<String> = Constants.DEFAULT_BLOCKED_KEYWORDS

    suspend fun detectInText(text: String, packageName: String): DetectionResult {
        // Check if keyword detection is enabled
        if (!settingsRepository.isKeywordDetectionEnabled().first()) {
            return DetectionResult(shouldBlock = false, packageName = packageName)
        }

        val lowerText = text.lowercase()
        val matchedKeyword = keywords.firstOrNull { keyword ->
            lowerText.contains(keyword)
        }

        return if (matchedKeyword != null) {
            DetectionResult(
                shouldBlock = true,
                reason = BlockReason.KEYWORD_DETECTED,
                details = "Keyword detected: $matchedKeyword",
                packageName = packageName,
                confidence = 1.0f
            )
        } else {
            DetectionResult(shouldBlock = false, packageName = packageName)
        }
    }

    suspend fun detectInUrl(url: String, packageName: String): DetectionResult {
        return detectInText(url, packageName)
    }
}