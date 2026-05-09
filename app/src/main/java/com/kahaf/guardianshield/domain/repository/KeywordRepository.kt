package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.KeywordRule
import kotlinx.coroutines.flow.Flow

interface KeywordRepository {
    fun observeAll(): Flow<List<KeywordRule>>
    suspend fun add(pattern: String, isRegex: Boolean): Long
    suspend fun update(rule: KeywordRule)
    suspend fun delete(id: Long)

    /** Returns the matching keyword pattern, or null if no match. */
    suspend fun firstMatch(text: String): KeywordRule?
}
