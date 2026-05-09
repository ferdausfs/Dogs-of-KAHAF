package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.KeywordRuleDao
import com.kahaf.guardianshield.data.db.entity.KeywordRuleEntity
import com.kahaf.guardianshield.domain.model.KeywordRule
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRepositoryImpl @Inject constructor(
    private val dao: KeywordRuleDao
) : KeywordRepository {

    /** Compiled regex cache. Invalidated whenever rules change. */
    @Volatile private var compiledCache: List<Pair<KeywordRule, Regex?>> = emptyList()
    @Volatile private var cacheVersion: Int = 0

    override fun observeAll(): Flow<List<KeywordRule>> =
        dao.observeAll().map { list ->
            val mapped = list.map { it.toDomain() }
            compiledCache = mapped.map { rule ->
                rule to runCatching {
                    if (rule.isRegex) Regex(rule.pattern, RegexOption.IGNORE_CASE) else null
                }.getOrNull()
            }
            cacheVersion++
            mapped
        }

    override suspend fun add(pattern: String, isRegex: Boolean): Long {
        if (isRegex) runCatching { Regex(pattern) }
            .onFailure { throw IllegalArgumentException("Invalid regex", it) }
        return dao.insert(
            KeywordRuleEntity(
                pattern = pattern,
                isRegex = isRegex,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun update(rule: KeywordRule) {
        if (rule.isRegex) runCatching { Regex(rule.pattern) }
            .onFailure { throw IllegalArgumentException("Invalid regex", it) }
        dao.update(rule.toEntity())
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun firstMatch(text: String): KeywordRule? {
        val haystack = text.lowercase()
        // Lazy-warm cache the first time
        if (compiledCache.isEmpty()) {
            val current = dao.observeAll().first()
            compiledCache = current.map { e ->
                e.toDomain() to runCatching {
                    if (e.isRegex) Regex(e.pattern, RegexOption.IGNORE_CASE) else null
                }.getOrNull()
            }
        }
        for ((rule, regex) in compiledCache) {
            val hit = if (rule.isRegex) {
                regex?.containsMatchIn(text) == true
            } else {
                haystack.contains(rule.pattern.lowercase())
            }
            if (hit) return rule
        }
        return null
    }

    private fun KeywordRuleEntity.toDomain() =
        KeywordRule(id, pattern, isRegex, createdAt)
    private fun KeywordRule.toEntity() =
        KeywordRuleEntity(id, pattern, isRegex, createdAt)
}
