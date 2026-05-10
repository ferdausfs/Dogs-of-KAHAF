package com.guardian.shield.service.detection

import com.guardian.shield.data.local.db.TimedBlockDao
import com.guardian.shield.data.local.db.TimedBlockEntity
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.util.GuardianConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v10 (2.1.0) — Source-based 15-min Timed Block.
 *
 * When the AccessibilityService confirms (after the EXPLICIT_DEBOUNCE) that
 * a content-source app (Facebook / Instagram / Twitter / TikTok / etc.) is
 * the actual source of EXPLICIT material on screen, that app is auto-locked
 * for [GuardianConstants.AI_SOURCE_BLOCK_MS] milliseconds.
 *
 * No overlay arguments. No second chances. The next time the user opens
 * that app within the lock window → straight to HOME + block overlay.
 *
 * Persistence: Room (`timed_blocks` table). Survives app restart and
 * device reboot. Expired entries are pruned opportunistically on every
 * read.
 *
 * Thread safety: a single in-memory snapshot is exposed as a hot StateFlow
 * (no per-call DB reads on the accessibility hot path).
 */
@Singleton
class TimedBlockManager @Inject constructor(
    private val dao: TimedBlockDao
) {
    private val mutex = Mutex()

    /** Hot in-memory cache: packageName → expiresAt. */
    private val _activeBlocks = MutableStateFlow<Map<String, Long>>(emptyMap())
    val activeBlocks: kotlinx.coroutines.flow.StateFlow<Map<String, Long>> =
        _activeBlocks.asStateFlow()

    /** Synchronous fast-path used by the accessibility hot loop. */
    fun isBlockedSync(pkg: String): Boolean {
        val expiresAt = _activeBlocks.value[pkg] ?: return false
        return System.currentTimeMillis() < expiresAt
    }

    /** Returns ms remaining or 0 if not blocked. */
    fun remainingMillis(pkg: String): Long {
        val expiresAt = _activeBlocks.value[pkg] ?: return 0L
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * Add a 15-min (default) timed block. Idempotent — calling twice on the
     * same package extends nothing; we keep the LATER expiry to avoid
     * accidentally shortening an already-active block.
     */
    suspend fun addTimedBlock(
        pkg: String,
        durationMs: Long = GuardianConstants.AI_SOURCE_BLOCK_MS,
        reason: BlockReason = BlockReason.AI_SOURCE_TIMED_BLOCK
    ) = mutex.withLock {
        val now = System.currentTimeMillis()
        val newExpiry = now + durationMs

        val existing = runCatching { dao.getActiveFor(pkg, now) }.getOrNull()
        val finalExpiry = if (existing != null && existing.expiresAt > newExpiry) {
            existing.expiresAt
        } else {
            newExpiry
        }

        runCatching {
            dao.upsert(
                TimedBlockEntity(
                    packageName = pkg,
                    expiresAt = finalExpiry,
                    reason = reason.name,
                    createdAt = now
                )
            )
        }.onFailure { Timber.w(it, "Failed to persist timed block for $pkg") }

        refreshCacheLocked(now)
        Timber.i("TimedBlock: $pkg locked until $finalExpiry (in ${(finalExpiry - now) / 1000}s)")
    }

    /** Manual unlock (used by Settings / dashboard "clear timed locks"). */
    suspend fun clear(pkg: String) = mutex.withLock {
        runCatching { dao.deleteByPackage(pkg) }
        refreshCacheLocked(System.currentTimeMillis())
    }

    suspend fun clearAll() = mutex.withLock {
        runCatching { dao.deleteAll() }
        _activeBlocks.value = emptyMap()
    }

    /**
     * Reload from DB. Called once at service startup and any time the
     * cache might be stale (rare).
     */
    suspend fun refresh() = mutex.withLock {
        refreshCacheLocked(System.currentTimeMillis())
    }

    private suspend fun refreshCacheLocked(now: Long) {
        runCatching { dao.pruneExpired(now) }
        val active = runCatching { dao.getActive(now) }.getOrDefault(emptyList())
        _activeBlocks.value = active.associate { it.packageName to it.expiresAt }
    }

    fun observeActive(): Flow<Map<String, Long>> = activeBlocks
}
