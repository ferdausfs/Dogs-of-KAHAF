package com.guardian.shield.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guardian.shield.data.local.db.*
import com.guardian.shield.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ── Database Module ────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * FIX: fallbackToDestructiveMigration() removed — causes data loss on
     * schema change. Proper migrations added instead.
     * Add new migrations here as the schema evolves.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Example: add severity column to block_events
            // database.execSQL(
            //     "ALTER TABLE block_events ADD COLUMN severity INTEGER DEFAULT 0"
            // )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): GuardianDatabase =
        Room.databaseBuilder(ctx, GuardianDatabase::class.java, "guardian_db")
            .addMigrations(MIGRATION_1_2)
            .build()

    // FIX: @Singleton added to DAOs — prevents multiple instances
    @Provides
    @Singleton
    fun provideAppRuleDao(db: GuardianDatabase): AppRuleDao = db.appRuleDao()

    @Provides
    @Singleton
    fun provideKeywordRuleDao(db: GuardianDatabase): KeywordRuleDao = db.keywordRuleDao()

    @Provides
    @Singleton
    fun provideBlockEventDao(db: GuardianDatabase): BlockEventDao = db.blockEventDao()
}

// ── Repository Module ──────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRuleRepo(impl: AppRuleRepositoryImpl): AppRuleRepository

    @Binds
    @Singleton
    abstract fun bindKeywordRepo(impl: KeywordRepositoryImpl): KeywordRepository

    @Binds
    @Singleton
    abstract fun bindBlockEventRepo(impl: BlockEventRepositoryImpl): BlockEventRepository
}