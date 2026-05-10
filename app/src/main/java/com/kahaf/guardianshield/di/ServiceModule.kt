package com.kahaf.guardianshield.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Service-layer Hilt module.
 *
 * v2.1.8: removed the redundant `provideSettingsDataStore` @Provides — it
 * collided with `SettingsDataStore`'s own `@Inject` constructor and would
 * have produced a Hilt "duplicate binding" error at compile time. The class
 * is now provided exclusively by its constructor.
 *
 * Kept as an empty module placeholder so future service-layer providers
 * (e.g. WorkManager helpers, system-service wrappers) have a home.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule
