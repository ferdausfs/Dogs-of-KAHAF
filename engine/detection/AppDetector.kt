package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDetector @Inject constructor(
    private val appRepository: AppRepository
) {
    // In-memory cache
    @Volatile
    private var cachedBlockedApps: Set<String> = emptySet()

    @Volatile
    private var lastCacheUpdate: Long = 0

    private val cacheValidityMs = 5000L

    suspend fun detect(packageName: String): DetectionResult {
        refreshCacheIfNeeded()

        return if (packageName in cachedBlockedApps) {
            DetectionResult(
                shouldBlock = true,
                reason = BlockReason.APP_BLOCKED,
                details = "App is in blocked list",
                packageName = packageName,
                confidence = 1.0f
            )
        } else {
            DetectionResult(shouldBlock = false, packageName = packageName)
        }
    }

    private suspend fun refreshCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate > cacheValidityMs) {
            cachedBlockedApps = appRepository.getBlockedPackages()
            lastCacheUpdate = now
        }
    }

    fun invalidateCache() {
        lastCacheUpdate = 0
    }
}