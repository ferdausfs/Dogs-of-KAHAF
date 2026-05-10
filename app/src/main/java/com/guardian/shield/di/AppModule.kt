package com.guardian.shield.di

import android.content.Context
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, "guardian.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun appRuleDao(db: GuardianDatabase) = db.appRuleDao()
    @Provides fun keywordDao(db: GuardianDatabase) = db.keywordDao()
    @Provides fun blockEventDao(db: GuardianDatabase) = db.blockEventDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindRulesRepository(impl: RulesRepositoryImpl): RulesRepository
}
