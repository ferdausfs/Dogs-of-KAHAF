package com.kahaf.guardian.engine.detection

import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDetector @Inject constructor(private val repo: AppRepository) {
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var lastUpdate: Long = 0

    suspend fun detect(pkg: String): DetectionResult {
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 5000) { cache = repo.getBlockedPackages(); lastUpdate = now }
        return if (pkg in cache) DetectionResult(true, BlockReason.APP_BLOCKED, "App is blocked", pkg, 1f)
        else DetectionResult(false, packageName = pkg)
    }

    fun invalidateCache() { lastUpdate = 0 }
}
