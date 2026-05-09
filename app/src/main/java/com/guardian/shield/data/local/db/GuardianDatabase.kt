package com.guardian.shield.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v9 (2.0.0):
 *  • Schema bumped to v2 to include the new `schedule_rules` table (P4-A).
 *  • Manual Migration(1 → 2) defined in AppModule.kt — AutoMigration was
 *    removed because it requires a committed `1.json` schema file in
 *    app/schemas/ which was absent, causing KSP to fail at build time.
 */
@Database(
    entities = [
        AppRuleEntity::class,
        KeywordRuleEntity::class,
        BlockEventEntity::class,
        ScheduleRuleEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun keywordDao(): KeywordDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
}