package com.kahaf.guardian.di

import android.content.Context
import com.kahaf.guardian.data.repository.AppRepositoryImpl
import com.kahaf.guardian.data.repository.BlockLogRepositoryImpl
import com.kahaf.guardian.data.repository.SettingsRepositoryImpl
import com.kahaf.guardian.domain.repository.AppRepository
import com.kahaf.guardian.domain.repository.BlockLogRepository
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.engine.blocking.BlockingEngine
import com.kahaf.guardian.engine.blocking.OverlayManager
import com.kahaf.guardian.engine.detection.*
import com.kahaf.guardian.engine.rules.RulesEngine
import com.kahaf.guardian.engine.rules.WhitelistChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindAppRepo(impl: AppRepositoryImpl): AppRepository
    @Binds @Singleton abstract fun bindBlockLogRepo(impl: BlockLogRepositoryImpl): BlockLogRepository
    @Binds @Singleton abstract fun bindSettingsRepo(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideWhitelistChecker(r: AppRepository) = WhitelistChecker(r)
    @Provides @Singleton fun provideAppDetector(r: AppRepository) = AppDetector(r)
    @Provides @Singleton fun provideKeywordDetector(r: SettingsRepository) = KeywordDetector(r)
    @Provides @Singleton fun provideAiDetector(@ApplicationContext c: Context, r: SettingsRepository) = AiDetector(c, r)
    @Provides @Singleton fun provideRulesEngine(w: WhitelistChecker, a: AppDetector, k: KeywordDetector, ai: AiDetector, s: SettingsRepository) = RulesEngine(w, a, k, ai, s)
    @Provides @Singleton fun provideOverlayManager(@ApplicationContext c: Context) = OverlayManager(c)
    @Provides @Singleton fun provideBlockingEngine(@ApplicationContext c: Context, o: OverlayManager, b: BlockLogRepository) = BlockingEngine(c, o, b)
    @Provides @Singleton fun provideOrchestrator(r: RulesEngine, b: BlockingEngine, s: SettingsRepository) = DetectionOrchestrator(r, b, s)
}
