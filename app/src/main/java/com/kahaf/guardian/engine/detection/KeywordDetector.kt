package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.util.Constants
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordDetector @Inject constructor(private val settings: SettingsRepository) {
    suspend fun detectInText(text: String, pkg: String): DetectionResult {
        if (!settings.isKeywordDetectionEnabled().first()) return DetectionResult(false, packageName = pkg)
        val lower = text.lowercase()
        val match = Constants.DEFAULT_BLOCKED_KEYWORDS.firstOrNull { lower.contains(it) }
        return if (match != null) DetectionResult(true, BlockReason.KEYWORD_DETECTED, "Keyword: $match", pkg, 1f)
        else DetectionResult(false, packageName = pkg)
    }
}
