package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.db.dao.DomainRuleDao
import com.kahaf.guardianshield.data.db.entity.DomainRuleEntity
import com.kahaf.guardianshield.domain.model.DomainRule
import com.kahaf.guardianshield.domain.repository.DomainRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainRepositoryImpl @Inject constructor(
    private val dao: DomainRuleDao
) : DomainRepository {

    override fun observeAll(): Flow<List<DomainRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<DomainRule> =
        dao.getAll().map { it.toDomain() }

    override suspend fun add(domain: String): Long {
        val normalized = normalize(domain)
        require(isValid(normalized)) { "Invalid domain format" }
        return dao.insert(
            DomainRuleEntity(
                domain = normalized,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun clear() = dao.deleteAll()

    private fun DomainRuleEntity.toDomain() = DomainRule(id, domain, createdAt)

    companion object {
        /** Strip protocol/path/whitespace and lowercase. */
        fun normalize(raw: String): String {
            var s = raw.trim().lowercase()
            s = s.removePrefix("http://").removePrefix("https://")
            s = s.substringBefore('/')
            s = s.substringBefore(':')
            s = s.removePrefix("www.")
            return s
        }

        fun isValid(domain: String): Boolean {
            if (domain.isBlank()) return false
            if (domain.contains(' ')) return false
            if (!domain.contains('.')) return false
            // Each label must be 1..63 chars of [a-z0-9-], not starting/ending with -.
            val labels = domain.split('.')
            if (labels.size < 2) return false
            return labels.all { l ->
                l.isNotEmpty() && l.length <= 63 &&
                        l.all { it.isLetterOrDigit() || it == '-' } &&
                        !l.startsWith('-') && !l.endsWith('-')
            }
        }
    }
}
