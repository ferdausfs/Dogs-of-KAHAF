package com.kahaf.guardian.data.repository

import com.kahaf.guardian.data.local.db.dao.BlockLogDao
import com.kahaf.guardian.data.local.db.entity.BlockLogEntity
import com.kahaf.guardian.domain.model.BlockEvent
import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockLogRepositoryImpl @Inject constructor(
    private val blockLogDao: BlockLogDao
) : BlockLogRepository {

    override suspend fun logBlockEvent(event: BlockEvent) {
        blockLogDao.insert(
            BlockLogEntity(
                packageName = event.packageName,
                reason = event.reason.name,
                details = event.details,
                timestamp = event.timestamp
            )
        )
    }

    override fun getTodayBlockCount(): Flow<Int> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return blockLogDao.getTodayCount(startOfDay)
    }

    override fun getTotalBlockCount(): Flow<Int> {
        return blockLogDao.getTotalCount()
    }

    override fun getRecentLogs(limit: Int): Flow<List<BlockEvent>> {
        return blockLogDao.getRecentLogs(limit).map { entities ->
            entities.map { entity ->
                BlockEvent(
                    id = entity.id,
                    packageName = entity.packageName,
                    reason = try {
                        BlockReason.valueOf(entity.reason)
                    } catch (e: Exception) {
                        BlockReason.MANUAL
                    },
                    details = entity.details,
                    timestamp = entity.timestamp
                )
            }
        }
    }
}