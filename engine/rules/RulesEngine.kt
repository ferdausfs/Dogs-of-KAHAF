package com.kahaf.guardian.engine.rules

import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.engine.detection.AiDetector
import com.kahaf.guardian.engine.detection.AppDetector
import com.kahaf.guardian.engine.detection.KeywordDetector
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RULES ENGINE
 *
 * CRITICAL RULE: Whitelist check is ALWAYS first.
 * If an app is whitelisted, SKIP ALL detection — NEVER block.
 *
 * Detection order:
 * 1. Whitelist check → if whitelisted, return safe
 * 2. App block list check → instant block if in list
 * 3. Keyword detection → if enabled
 * 4. AI detection → if enabled (optional)
 */
@Singleton
class RulesEngine @Inject constructor(
    private val whitelistChecker: WhitelistChecker,
    private val appDetector: AppDetector,
    private val keywordDetector: KeywordDetector,
    private val aiDetector: AiDetector,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Evaluate whether a foreground package should be blocked.
     */
    suspend fun evaluate(packageName: String): DetectionResult {
        // ========== STEP 1: WHITELIST CHECK (HIGHEST PRIORITY) ==========
        if (whitelistChecker.isWhitelisted(packageName)) {
            return DetectionResult(
                shouldBlock = false,
                packageName = packageName,
                details = "Whitelisted - skipping all detection"
            )
        }

        // ========== STEP 2: APP BLOCK LIST CHECK ==========
        val appResult = appDetector.detect(packageName)
        if (appResult.shouldBlock) {
            return appResult
        }

        // Non-blocked, non-whitelisted apps pass through
        return DetectionResult(shouldBlock = false, packageName = packageName)
    }

    /**
     * Evaluate text content (URLs, screen text) for keyword matching.
     * Whitelist STILL takes priority.
     */
    suspend fun evaluateText(text: String, packageName: String): DetectionResult {
        // ========== STEP 1: WHITELIST CHECK (HIGHEST PRIORITY) ==========
        if (whitelistChecker.isWhitelisted(packageName)) {
            return DetectionResult(
                shouldBlock = false,
                packageName = packageName,
                details = "Whitelisted - skipping text detection"
            )
        }

        // ========== STEP 2: KEYWORD DETECTION ==========
        if (settingsRepository.isKeywordDetectionEnabled().first()) {
            val keywordResult = keywordDetector.detectInText(text, packageName)
            if (keywordResult.shouldBlock) {
                return keywordResult
            }
        }

        return DetectionResult(shouldBlock = false, packageName = packageName)
    }
}