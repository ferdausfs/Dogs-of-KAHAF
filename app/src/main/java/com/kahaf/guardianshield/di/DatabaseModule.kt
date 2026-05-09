package com.kahaf.guardianshield.di

import android.content.Context
import androidx.room.Room
import com.kahaf.guardianshield.data.db.GuardianDatabase
import com.kahaf.guardianshield.data.db.Migrations
import com.kahaf.guardianshield.data.db.dao.AppLockDao
import com.kahaf.guardianshield.data.db.dao.AppRuleDao
import com.kahaf.guardianshield.data.db.dao.BlockEventDao
import com.kahaf.guardianshield.data.db.dao.KeywordRuleDao
import com.kahaf.guardianshield.data.db.dao.ScheduleDao
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
    fun provideDatabase(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, GuardianDatabase.DB_NAME)
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideAppRuleDao(db: GuardianDatabase): AppRuleDao = db.appRuleDao()
    @Provides fun provideKeywordDao(db: GuardianDatabase): KeywordRuleDao = db.keywordRuleDao()
    @Provides fun provideScheduleDao(db: GuardianDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideBlockEventDao(db: GuardianDatabase): BlockEventDao = db.blockEventDao()
    @Provides fun provideAppLockDao(db: GuardianDatabase): AppLockDao = db.appLockDao()
}
