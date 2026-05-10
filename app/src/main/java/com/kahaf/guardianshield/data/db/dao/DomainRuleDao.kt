package com.kahaf.guardianshield.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kahaf.guardianshield.data.db.entity.DomainRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainRuleDao {

    @Query("SELECT * FROM domain_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<DomainRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: DomainRuleEntity): Long

    @Query("DELETE FROM domain_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM domain_rules")
    suspend fun deleteAll()
}
