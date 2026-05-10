package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.BlockEventDao
import com.kahaf.guardianshield.data.db.entity.BlockEventEntity
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class BlockEventRepositoryImpl @Inject constructor(
    private val dao: BlockEventDao
) : BlockEventRepository {

    override fun observeRecent(limit: Int): Flow<List<BlockEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /**
     * v2.1.8 fix: previously `startOfTodayMs()` was captured once at flow
     * construction time, which made the count stale after midnight. The
     * outer `flow { … }` builder now re-emits a fresh start-of-day every
     * minute, and `flatMapLatest` cancels the prior DAO subscription when
     * the day rolls over.
     */
    override fun observeBlocksTodayCount(): Flow<Int> {
        val tickerSourceMs = TimeUnit.MINUTES.toMillis(1)
        val source: Flow<Long> = flow {
            while (true) {
                emit(startOfTodayMs())
                kotlinx.coroutines.delay(tickerSourceMs)
            }
        }
        return source
            .map { it } // identity, kept for clarity
            .flatMapLatest { since -> dao.observeCountSince(since) }
    }

    override suspend fun log(packageName: String, reason: BlockReason, detail: String) {
        dao.insert(
            BlockEventEntity(
                packageName = packageName,
                reason = reason.name,
                detail = detail,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun pruneOlderThan(olderThanMs: Long) = dao.pruneOlderThan(olderThanMs)
    override suspend fun clear() = dao.deleteAll()

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun BlockEventEntity.toDomain() = BlockEvent(
        id = id,
        packageName = packageName,
        reason = BlockReason.fromStringOrDefault(reason),
        detail = detail,
        timestamp = timestamp
    )
}
