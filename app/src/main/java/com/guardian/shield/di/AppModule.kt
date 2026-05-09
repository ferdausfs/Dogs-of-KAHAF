package com.guardian.shield.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guardian.shield.data.local.db.*
import com.guardian.shield.data.repository.RulesRepositoryImpl
import com.guardian.shield.domain.repository.RulesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Manual migration v1 → v2.
 * Adds the `schedule_rules` table (P4-A time-based schedule blocking).
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
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

/**
 * v10 (2.1.0): manual migration v2 → v3.
 * Adds the `timed_blocks` table for the source-based 15-min auto-lock.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `timed_blocks` (
                `packageName` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `reason` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`packageName`)
            )
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, "guardian.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun appRuleDao(db: GuardianDatabase) = db.appRuleDao()
    @Provides fun keywordDao(db: GuardianDatabase) = db.keywordDao()
    @Provides fun blockEventDao(db: GuardianDatabase) = db.blockEventDao()
    @Provides fun scheduleRuleDao(db: GuardianDatabase) = db.scheduleRuleDao()
    @Provides fun timedBlockDao(db: GuardianDatabase) = db.timedBlockDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindRulesRepository(impl: RulesRepositoryImpl): RulesRepository
}
