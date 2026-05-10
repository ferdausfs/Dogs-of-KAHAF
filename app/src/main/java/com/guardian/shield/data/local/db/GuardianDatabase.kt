package com.guardian.shield.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v10 (2.1.0):
 *  • Schema bumped to v3 — adds `timed_blocks` table for the source-based
 *    15-min auto-lock feature. Manual Migration(2 → 3) defined in
 *    AppModule.kt.
 *
 * v9 (2.0.0):
 *  • Schema bumped to v2 to include the new `schedule_rules` table (P4-A).
 */
@Database(
    entities = [
        AppRuleEntity::class,
        KeywordRuleEntity::class,
        BlockEventEntity::class,
        ScheduleRuleEntity::class,
        TimedBlockEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun keywordDao(): KeywordDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun timedBlockDao(): TimedBlockDao
}
