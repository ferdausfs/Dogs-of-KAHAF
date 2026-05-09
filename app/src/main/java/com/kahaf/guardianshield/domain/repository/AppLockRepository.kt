package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.AppLock
import kotlinx.coroutines.flow.Flow

interface AppLockRepository {
    fun observeAll(): Flow<List<AppLock>>
    suspend fun isLocked(pkg: String, nowMs: Long = System.currentTimeMillis()): AppLock?
    suspend fun lockFor(pkg: String, durationMs: Long, reason: String)
    suspend fun clear(pkg: String)
    suspend fun pruneExpired()
}
