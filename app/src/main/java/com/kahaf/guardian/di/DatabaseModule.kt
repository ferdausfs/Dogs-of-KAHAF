package com.kahaf.guardian.di

import android.content.Context
import androidx.room.Room
import com.kahaf.guardian.data.local.db.KahafDatabase
import com.kahaf.guardian.data.local.db.dao.AppDao
import com.kahaf.guardian.data.local.db.dao.BlockLogDao
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
    fun provideDatabase(@ApplicationContext ctx: Context): KahafDatabase =
        Room.databaseBuilder(ctx, KahafDatabase::class.java, "kahaf_guardian.db")
            .fallbackToDestructiveMigration().build()

    @Provides fun provideAppDao(db: KahafDatabase): AppDao = db.appDao()
    @Provides fun provideBlockLogDao(db: KahafDatabase): BlockLogDao = db.blockLogDao()
}
