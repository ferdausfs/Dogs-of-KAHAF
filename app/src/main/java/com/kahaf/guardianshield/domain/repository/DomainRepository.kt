package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.DomainRule
import kotlinx.coroutines.flow.Flow

interface DomainRepository {
    fun observeAll(): Flow<List<DomainRule>>
    suspend fun getAll(): List<DomainRule>
    suspend fun add(domain: String): Long
    suspend fun delete(id: Long)
    suspend fun clear()
}
