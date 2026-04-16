package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.engine.blocking.BlockingEngine
import com.kahaf.guardian.engine.rules.RulesEngine
import com.kahaf.guardian.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionOrchestrator @Inject constructor(
    private val rulesEngine: RulesEngine,
    private val blockingEngine: BlockingEngine,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastProcessedPackage: String = ""

    @Volatile
    private var lastProcessedTime: Long = 0

    /**
     * Main entry point for processing a foreground app change event.
     * Called from AccessibilityService.
     */
    fun onAppChanged(packageName: String) {
        // Debounce - avoid processing same package repeatedly
        val now = System.currentTimeMillis()
        if (packageName == lastProcessedPackage &&
            now - lastProcessedTime < Constants.DEBOUNCE_MS
        ) {
            return
        }
        lastProcessedPackage = packageName
        lastProcessedTime = now

        scope.launch {
            // Check if protection is active
            if (!settingsRepository.isProtectionActive().first()) return@launch

            val result = rulesEngine.evaluate(packageName)
            if (result.shouldBlock) {
                blockingEngine.executeBlock(result)
            }
        }
    }

    /**
     * Process screen text from accessibility events.
     * Called for keyword detection in browser URLs and screen text.
     */
    fun onScreenTextDetected(text: String, packageName: String) {
        scope.launch {
            if (!settingsRepository.isProtectionActive().first()) return@launch

            val result = rulesEngine.evaluateText(text, packageName)
            if (result.shouldBlock) {
                blockingEngine.executeBlock(result)
            }
        }
    }
}