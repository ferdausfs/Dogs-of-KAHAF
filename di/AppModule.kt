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
import com.kahaf.guardian.engine.detection.AiDetector
import com.kahaf.guardian.engine.detection.AppDetector
import com.kahaf.guardian.engine.detection.DetectionOrchestrator
import com.kahaf.guardian.engine.detection.KeywordDetector
import com.kahaf.guardian.engine.rules.RulesEngine
import com.kahaf.guardian.engine.rules.WhitelistChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindBlockLogRepository(impl: BlockLogRepositoryImpl): BlockLogRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWhitelistChecker(appRepository: AppRepository): WhitelistChecker {
        return WhitelistChecker(appRepository)
    }

    @Provides
    @Singleton
    fun provideAppDetector(appRepository: AppRepository): AppDetector {
        return AppDetector(appRepository)
    }

    @Provides
    @Singleton
    fun provideKeywordDetector(settingsRepository: SettingsRepository): KeywordDetector {
        return KeywordDetector(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideAiDetector(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): AiDetector {
        return AiDetector(context, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideRulesEngine(
        whitelistChecker: WhitelistChecker,
        appDetector: AppDetector,
        keywordDetector: KeywordDetector,
        aiDetector: AiDetector,
        settingsRepository: SettingsRepository
    ): RulesEngine {
        return RulesEngine(whitelistChecker, appDetector, keywordDetector, aiDetector, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideOverlayManager(@ApplicationContext context: Context): OverlayManager {
        return OverlayManager(context)
    }

    @Provides
    @Singleton
    fun provideBlockingEngine(
        @ApplicationContext context: Context,
        overlayManager: OverlayManager,
        blockLogRepository: BlockLogRepository
    ): BlockingEngine {
        return BlockingEngine(context, overlayManager, blockLogRepository)
    }

    @Provides
    @Singleton
    fun provideDetectionOrchestrator(
        rulesEngine: RulesEngine,
        blockingEngine: BlockingEngine,
        settingsRepository: SettingsRepository
    ): DetectionOrchestrator {
        return DetectionOrchestrator(rulesEngine, blockingEngine, settingsRepository)
    }
}