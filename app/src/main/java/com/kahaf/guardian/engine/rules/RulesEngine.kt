package com.kahaf.guardian.engine.rules

import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.engine.detection.AiDetector
import com.kahaf.guardian.engine.detection.AppDetector
import com.kahaf.guardian.engine.detection.KeywordDetector
import kotlinx.coroutines.flow.first

class RulesEngine constructor(
    private val wl: WhitelistChecker, private val ad: AppDetector,
    private val kd: KeywordDetector, private val ai: AiDetector,
    private val settings: SettingsRepository
) {
    suspend fun evaluate(pkg: String): DetectionResult {
        if (wl.isWhitelisted(pkg)) return DetectionResult(false, packageName = pkg, details = "Whitelisted")
        val r = ad.detect(pkg); if (r.shouldBlock) return r
        return DetectionResult(false, packageName = pkg)
    }

    suspend fun evaluateText(text: String, pkg: String): DetectionResult {
        if (wl.isWhitelisted(pkg)) return DetectionResult(false, packageName = pkg, details = "Whitelisted")
        if (settings.isKeywordDetectionEnabled().first()) { val r = kd.detectInText(text, pkg); if (r.shouldBlock) return r }
        return DetectionResult(false, packageName = pkg)
    }
}
