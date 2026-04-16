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
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val blockLogRepository: BlockLogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastBlockedPackage: String = ""

    @Volatile
    private var lastBlockTime: Long = 0

    // Avoid blocking same app within 2 seconds
    private val blockCooldownMs = 2000L

    fun executeBlock(result: DetectionResult) {
        val now = System.currentTimeMillis()
        if (result.packageName == lastBlockedPackage &&
            now - lastBlockTime < blockCooldownMs
        ) {
            return
        }
        lastBlockedPackage = result.packageName
        lastBlockTime = now

        // Show block screen
        overlayManager.showBlockScreen(result)

        // Log the block event
        scope.launch {
            try {
                val event = BlockEvent(
                    packageName = result.packageName,
                    reason = result.reason!!,
                    details = result.details,
                    timestamp = now
                )
                blockLogRepository.logBlockEvent(event)
            } catch (e: Exception) {
                // Don't crash on logging failure
            }
        }
    }
}