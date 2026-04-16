package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.engine.blocking.BlockingEngine
import com.kahaf.guardian.engine.rules.RulesEngine
import com.kahaf.guardian.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DetectionOrchestrator constructor(
    private val rules: RulesEngine, private val blocker: BlockingEngine,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastPkg = ""
    @Volatile private var lastTime = 0L

    fun onAppChanged(pkg: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastTime < Constants.DEBOUNCE_MS) return
        lastPkg = pkg; lastTime = now
        scope.launch {
            if (!settings.isProtectionActive().first()) return@launch
            val r = rules.evaluate(pkg); if (r.shouldBlock) blocker.executeBlock(r)
        }
    }

    fun onScreenTextDetected(text: String, pkg: String) {
        scope.launch {
            if (!settings.isProtectionActive().first()) return@launch
            val r = rules.evaluateText(text, pkg); if (r.shouldBlock) blocker.executeBlock(r)
        }
    }
}
