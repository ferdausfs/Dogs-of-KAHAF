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
 * v11 (2.1.1) STABILITY PATCH:
 *  • Migration v1→v2 and v2→v3 are now wrapped with IF NOT EXISTS so a
 *    re-run on a partially-migrated DB does not crash.
 *  • fallbackToDestructiveMigrationOnDowngrade() added so downgrading
 *    (e.g. test debug → prod release) doesn't crash with
 *    IllegalStateException.
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
            .fallbackToDestructiveMigrationOnDowngrade()
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
