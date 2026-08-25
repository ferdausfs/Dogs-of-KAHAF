package com.guardian.shield.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppRuleEntity::class,
        KeywordRuleEntity::class,
        BlockEventEntity::class,
        ScheduleRuleEntity::class,
        PendingReportEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun keywordDao(): KeywordDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun pendingReportDao(): PendingReportDao

    companion object {
        const val DB_NAME = "guardian.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_rules` (
                        `packageName` TEXT NOT NULL,
                        `startHour` INTEGER NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endHour` INTEGER NOT NULL,
                        `endMinute` INTEGER NOT NULL,
                        `enabledDaysMask` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        // TASK B — cooling-off queue table.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_reports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `timestampCreated` INTEGER NOT NULL,
                        `scheduledApplyAt` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `source` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `strikeCount` INTEGER NOT NULL DEFAULT 0,
                        `delayMs` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        // v3.6.1 — persist the reported image signature so the cooling-off
        // worker applies the ORIGINAL pattern, not whatever pendingCandidate
        // happens to be in memory hours later.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pending_reports` ADD COLUMN `signatureCsv` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // R7.7 — multi-window schedules: schedule_rules PK moves from
        // packageName to autogen id. Column-preserving table rebuild —
        // every existing window keeps its values, ids get reassigned.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `startHour` INTEGER NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endHour` INTEGER NOT NULL,
                        `endMinute` INTEGER NOT NULL,
                        `enabledDaysMask` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `schedule_rules_new`
                        (`packageName`, `startHour`, `startMinute`, `endHour`,
                         `endMinute`, `enabledDaysMask`, `enabled`, `createdAt`)
                    SELECT `packageName`, `startHour`, `startMinute`, `endHour`,
                           `endMinute`, `enabledDaysMask`, `enabled`, `createdAt`
                    FROM `schedule_rules`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `schedule_rules`")
                db.execSQL("ALTER TABLE `schedule_rules_new` RENAME TO `schedule_rules`")
            }
        }

        // R12 (v3.8.2) — 3-minute undo-grace stamp on app blocks. Existing
        // blocked rows get 0 = legacy permanent block (no surprise grace).
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `app_rules` ADD COLUMN `blockedAtMs` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
