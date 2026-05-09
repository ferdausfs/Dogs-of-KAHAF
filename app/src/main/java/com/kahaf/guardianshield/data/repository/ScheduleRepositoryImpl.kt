package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.ScheduleDao
import com.kahaf.guardianshield.data.db.entity.ScheduleEntity
import com.kahaf.guardianshield.domain.model.Schedule
import com.kahaf.guardianshield.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val dao: ScheduleDao
) : ScheduleRepository {

    override fun observeAll(): Flow<List<Schedule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabled(): List<Schedule> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun upsert(schedule: Schedule): Long =
        dao.insert(schedule.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun ScheduleEntity.toDomain() = Schedule(
        id = id,
        label = label,
        daysMask = daysMask,
        startMin = startMin,
        endMin = endMin,
        packages = packagesCsv.split(',').filter { it.isNotBlank() },
        enabled = enabled
    )

    private fun Schedule.toEntity() = ScheduleEntity(
        id = id,
        label = label,
        daysMask = daysMask,
        startMin = startMin,
        endMin = endMin,
        packagesCsv = packages.joinToString(","),
        enabled = enabled
    )
}
