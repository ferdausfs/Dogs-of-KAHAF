package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.AppLockDao
import com.kahaf.guardianshield.data.db.entity.AppLockEntity
import com.kahaf.guardianshield.domain.model.AppLock
import com.kahaf.guardianshield.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockRepositoryImpl @Inject constructor(
    private val dao: AppLockDao
) : AppLockRepository {

    override fun observeAll(): Flow<List<AppLock>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun isLocked(pkg: String, nowMs: Long): AppLock? {
        val row = dao.get(pkg) ?: return null
        return if (row.lockedUntilEpochMs > nowMs) row.toDomain() else null
    }

    override suspend fun lockFor(pkg: String, durationMs: Long, reason: String) {
        dao.upsert(
            AppLockEntity(
                packageName = pkg,
                lockedUntilEpochMs = System.currentTimeMillis() + durationMs,
                reason = reason
            )
        )
    }

    override suspend fun clear(pkg: String) = dao.delete(pkg)

    override suspend fun pruneExpired() =
        dao.pruneExpired(System.currentTimeMillis())

    private fun AppLockEntity.toDomain() =
        AppLock(packageName, lockedUntilEpochMs, reason)
}
