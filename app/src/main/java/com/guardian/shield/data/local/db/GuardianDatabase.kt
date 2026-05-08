package com.guardian.shield.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v9 (2.0.0):
 *  • Schema bumped to v2 to include the new `schedule_rules` table (P4-A).
 *  • AutoMigration(1 → 2) handles upgrade non-destructively.
 */
@Database(
    entities = [
        AppRuleEntity::class,
        KeywordRuleEntity::class,
        BlockEventEntity::class,
        ScheduleRuleEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun keywordDao(): KeywordDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
}
