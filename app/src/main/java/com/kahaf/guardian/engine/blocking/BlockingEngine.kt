package com.kahaf.guardian.engine.blocking

import android.content.Context
import com.kahaf.guardian.domain.model.BlockEvent
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingEngine @Inject constructor(
    private val ctx: Context, private val overlay: OverlayManager,
    private val logRepo: BlockLogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastPkg = ""
    @Volatile private var lastTime = 0L

    fun executeBlock(result: DetectionResult) {
        val now = System.currentTimeMillis()
        if (result.packageName == lastPkg && now - lastTime < 2000) return
        lastPkg = result.packageName; lastTime = now
        overlay.showBlockScreen(result)
        scope.launch {
            try { logRepo.logBlockEvent(BlockEvent(packageName = result.packageName, reason = result.reason!!, details = result.details, timestamp = now)) }
            catch (_: Exception) {}
        }
    }
}
