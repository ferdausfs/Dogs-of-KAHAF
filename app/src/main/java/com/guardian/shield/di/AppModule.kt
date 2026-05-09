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
 * v13 (2.1.3) STABILITY PATCH 3:
 *  • IF NOT EXISTS migrations + downgrade fallback — kept verbatim from v11.
 *  • NB: we deliberately keep the no-arg `fallbackToDestructiveMigration()`
 *    form because the `dropAllTables: Boolean` overload was only added in
 *    Room 2.7.0+. We're pinned to 2.6.1, where calling the new overload
 *    would FAIL TO COMPILE. The no-arg form has the exact same effect for
 *    our purposes (drops all tables on schema mismatch); it just produces
 *    a deprecation warning, which we accept until we bump Room.
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
    @Suppress("DEPRECATION") // v13: see file-level note about Room 2.6.1.
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
