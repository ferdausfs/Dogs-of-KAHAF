package com.kahaf.guardianshield.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kahaf.guardianshield.data.db.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {

    @Query("SELECT * FROM keyword_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KeywordRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: KeywordRuleEntity): Long

    @Update
    suspend fun update(rule: KeywordRuleEntity)

    @Delete
    suspend fun delete(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
