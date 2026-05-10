package com.kahaf.guardianshield.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Idempotent migrations.
 *
 * v1 → v2 : added `schedules` and `block_events` tables.
 * v2 → v3 : added `app_locks` table for source-based 15-minute auto-lock.
 * v3 → v4 : added `domain_rules` table for browser domain blocking (v3.0.0).
 *
 * We use IF NOT EXISTS everywhere so re-running a partially applied migration
 * never crashes the user's database.
 */
object Migrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    label TEXT NOT NULL,
                    daysMask INTEGER NOT NULL,
                    startMin INTEGER NOT NULL,
                    endMin INTEGER NOT NULL,
                    packagesCsv TEXT NOT NULL,
                    enabled INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS block_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    packageName TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_locks (
                    packageName TEXT NOT NULL PRIMARY KEY,
                    lockedUntilEpochMs INTEGER NOT NULL,
                    reason TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS domain_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    domain TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
