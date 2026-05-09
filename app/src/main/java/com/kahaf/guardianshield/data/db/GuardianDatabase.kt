package com.kahaf.guardianshield.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kahaf.guardianshield.data.db.dao.AppLockDao
import com.kahaf.guardianshield.data.db.dao.AppRuleDao
import com.kahaf.guardianshield.data.db.dao.BlockEventDao
import com.kahaf.guardianshield.data.db.dao.KeywordRuleDao
import com.kahaf.guardianshield.data.db.dao.ScheduleDao
import com.kahaf.guardianshield.data.db.entity.AppLockEntity
import com.kahaf.guardianshield.data.db.entity.AppRuleEntity
import com.kahaf.guardianshield.data.db.entity.BlockEventEntity
import com.kahaf.guardianshield.data.db.entity.KeywordRuleEntity
import com.kahaf.guardianshield.data.db.entity.ScheduleEntity

@Database(
    entities = [
        AppRuleEntity::class,
        KeywordRuleEntity::class,
        ScheduleEntity::class,
        BlockEventEntity::class,
        AppLockEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun appLockDao(): AppLockDao

    companion object {
        const val DB_NAME = "guardian_shield.db"
    }
}
