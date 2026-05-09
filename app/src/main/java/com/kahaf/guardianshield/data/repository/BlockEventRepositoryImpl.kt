package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.BlockEventDao
import com.kahaf.guardianshield.data.db.entity.BlockEventEntity
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlockEventRepositoryImpl @Inject constructor(
    private val dao: BlockEventDao
) : BlockEventRepository {

    override fun observeRecent(limit: Int): Flow<List<BlockEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeBlocksTodayCount(): Flow<Int> {
        val startOfDay = startOfTodayMs()
        return flowOf(startOfDay).flatMapLatest { since -> dao.observeCountSince(since) }
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
