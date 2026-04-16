package com.kahaf.guardian.engine.rules

import com.kahaf.guardian.domain.repository.AppRepository
import com.kahaf.guardian.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HIGHEST PRIORITY CHECK
 * If an app is whitelisted, it MUST NEVER be blocked.
 * This check runs BEFORE any other detection.
 */
@Singleton
class WhitelistChecker @Inject constructor(
    private val appRepository: AppRepository
) {
    // In-memory cache for fast lookup
    @Volatile
    private var cachedWhitelist: Set<String> = emptySet()

    @Volatile
    private var lastCacheUpdate: Long = 0

    private val cacheValidityMs = 5000L // Refresh cache every 5 seconds

    suspend fun isWhitelisted(packageName: String): Boolean {
        // Our own package is always whitelisted
        if (packageName in Constants.SYSTEM_PROTECTED_PACKAGES) return true

        refreshCacheIfNeeded()
        return packageName in cachedWhitelist
    }

    suspend fun refreshCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate > cacheValidityMs) {
            cachedWhitelist = appRepository.getWhitelistedPackages()
            lastCacheUpdate = now
        }
    }

    fun invalidateCache() {
        lastCacheUpdate = 0
    }
}