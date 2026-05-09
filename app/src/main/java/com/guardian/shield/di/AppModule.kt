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
 * Replaces AutoMigration which required a committed 1.json schema file.
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, "guardian.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun appRuleDao(db: GuardianDatabase) = db.appRuleDao()
    @Provides fun keywordDao(db: GuardianDatabase) = db.keywordDao()
    @Provides fun blockEventDao(db: GuardianDatabase) = db.blockEventDao()
    @Provides fun scheduleRuleDao(db: GuardianDatabase) = db.scheduleRuleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindRulesRepository(impl: RulesRepositoryImpl): RulesRepository
}