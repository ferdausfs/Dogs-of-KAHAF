package com.guardian.shield.di

import android.content.Context
import androidx.room.Room
import com.guardian.shield.data.local.db.AppRuleDao
import com.guardian.shield.data.local.db.BlockEventDao
import com.guardian.shield.data.local.db.GuardianDatabase
import com.guardian.shield.data.local.db.KeywordDao
import com.guardian.shield.data.local.db.ScheduleRuleDao
import com.guardian.shield.data.repository.RulesRepositoryImpl
import com.guardian.shield.domain.repository.RulesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): GuardianDatabase =
        Room.databaseBuilder(ctx, GuardianDatabase::class.java, GuardianDatabase.DB_NAME)
            .addMigrations(
                GuardianDatabase.MIGRATION_1_2,
                GuardianDatabase.MIGRATION_2_3
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun appRuleDao(db: GuardianDatabase): AppRuleDao = db.appRuleDao()
    @Provides fun keywordDao(db: GuardianDatabase): KeywordDao = db.keywordDao()
    @Provides fun blockEventDao(db: GuardianDatabase): BlockEventDao = db.blockEventDao()
    @Provides fun scheduleRuleDao(db: GuardianDatabase): ScheduleRuleDao = db.scheduleRuleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRulesRepository(impl: RulesRepositoryImpl): RulesRepository
}
