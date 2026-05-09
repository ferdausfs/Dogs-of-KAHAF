package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeAll(): Flow<List<Schedule>>
    suspend fun getEnabled(): List<Schedule>
    suspend fun upsert(schedule: Schedule): Long
    suspend fun delete(id: Long)
}
