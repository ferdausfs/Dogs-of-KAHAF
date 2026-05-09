package com.kahaf.guardianshield.di

import com.kahaf.guardianshield.data.classifier.StubNsfwClassifier
import com.kahaf.guardianshield.data.repository.AppLockRepositoryImpl
import com.kahaf.guardianshield.data.repository.AppRuleRepositoryImpl
import com.kahaf.guardianshield.data.repository.BlockEventRepositoryImpl
import com.kahaf.guardianshield.data.repository.KeywordRepositoryImpl
import com.kahaf.guardianshield.data.repository.ScheduleRepositoryImpl
import com.kahaf.guardianshield.data.repository.SettingsRepositoryImpl
import com.kahaf.guardianshield.domain.repository.AppLockRepository
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import com.kahaf.guardianshield.domain.repository.NsfwClassifier
import com.kahaf.guardianshield.domain.repository.ScheduleRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAppRuleRepository(impl: AppRuleRepositoryImpl): AppRuleRepository

    @Binds @Singleton
    abstract fun bindKeywordRepository(impl: KeywordRepositoryImpl): KeywordRepository

    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds @Singleton
    abstract fun bindBlockEventRepository(impl: BlockEventRepositoryImpl): BlockEventRepository

    @Binds @Singleton
    abstract fun bindAppLockRepository(impl: AppLockRepositoryImpl): AppLockRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    /**
     * The default classifier is the stub — keeps builds green even without
     * `assets/nsfw_v1.tflite`. Swap to TfLiteNsfwClassifier in a custom build
     * variant by replacing this binding.
     */
    @Binds @Singleton
    abstract fun bindNsfwClassifier(impl: StubNsfwClassifier): NsfwClassifier
}
