package com.kahaf.guardian.engine.rules

import com.kahaf.guardian.domain.repository.AppRepository
import com.kahaf.guardian.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistChecker @Inject constructor(private val repo: AppRepository) {
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var lastUpdate: Long = 0

    suspend fun isWhitelisted(pkg: String): Boolean {
        if (pkg in Constants.SYSTEM_PROTECTED_PACKAGES) return true
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 5000) { cache = repo.getWhitelistedPackages(); lastUpdate = now }
        return pkg in cache
    }

    fun invalidateCache() { lastUpdate = 0 }
}
