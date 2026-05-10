package com.kahaf.guardianshield.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "guardian_settings")

/**
 * On-device settings store. Single Hilt binding via `@Inject` constructor +
 * `@ApplicationContext`. We removed the duplicate `@Provides` in ServiceModule
 * because two bindings for the same type cause a duplicate-binding compile
 * error in Hilt.
 *
 * v3.0.0:
 *  - removed `AI_ENGINE` (no longer user-selectable; the real TFLite classifier
 *    is bound unconditionally — see [com.kahaf.guardianshield.di.RepositoryModule]).
 *  - added `AI_HEURISTIC`, `AI_MIN_IMAGE_SIZE`, `AI_INPUT_NORMALIZED`.
 *  - added `PIN_HASH`, `PIN_ENABLED` for Settings PIN lock.
 *
 * v2.1.8 fixes (preserved):
 *  - Reads no longer use `dataStore.edit{}` (which would write on every read).
 *  - `updateApp` / `updateAi` use a single `edit{}` block so concurrent
 *    transforms cannot race.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val UNINSTALL_PROTECT = booleanPreferencesKey("uninstall_protection")
        val PIN_HASH = stringPreferencesKey("settings_pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("settings_pin_enabled")

        val AI_SENSITIVITY = floatPreferencesKey("ai_sensitivity")
        val AI_DEBOUNCE_FRAMES = intPreferencesKey("ai_debounce_frames")
        val AI_DEBOUNCE_WINDOW = longPreferencesKey("ai_debounce_window_ms")
        val AI_PER_APP_BOOST = stringPreferencesKey("ai_per_app_boost_csv") // pkg=boost,...
        val AI_SOURCES = stringPreferencesKey("ai_content_sources_csv")
        val AI_HEURISTIC = booleanPreferencesKey("ai_heuristic_enabled")
        val AI_MIN_IMAGE_SIZE = intPreferencesKey("ai_min_image_size")
        val AI_INPUT_NORMALIZED = booleanPreferencesKey("ai_input_normalized")
    }

    val appSettings: Flow<AppSettings> = context.dataStore.data.map { prefs -> decodeApp(prefs) }
    val aiSettings: Flow<AiSettings> = context.dataStore.data.map { prefs -> decodeAi(prefs) }

    suspend fun updateApp(transform: (AppSettings) -> AppSettings) {
        // Atomic read-modify-write inside a single edit block.
        context.dataStore.edit { p ->
            val current = decodeApp(p)
            val next = transform(current)
            p[Keys.PROTECTION] = next.protectionEnabled
            p[Keys.THEME_MODE] = next.themeMode.name
            p[Keys.DYNAMIC_COLOR] = next.dynamicColor
            p[Keys.UNINSTALL_PROTECT] = next.uninstallProtection
            p[Keys.PIN_HASH] = next.settingsPinHash
            p[Keys.PIN_ENABLED] = next.settingsPinEnabled
        }
    }

    suspend fun updateAi(transform: (AiSettings) -> AiSettings) {
        context.dataStore.edit { p ->
            val current = decodeAi(p)
            val next = transform(current)
            p[Keys.AI_SENSITIVITY] = next.sensitivity
            p[Keys.AI_DEBOUNCE_FRAMES] = next.debounceFrames
            p[Keys.AI_DEBOUNCE_WINDOW] = next.debounceWindowMs
            p[Keys.AI_PER_APP_BOOST] = encodeBoosts(next.perAppBoost)
            p[Keys.AI_SOURCES] = next.contentSourcePackages.joinToString(",")
            p[Keys.AI_HEURISTIC] = next.heuristicEnabled
            p[Keys.AI_MIN_IMAGE_SIZE] = next.minImageSize
            p[Keys.AI_INPUT_NORMALIZED] = next.modelInputNormalized
        }
    }

    private fun decodeApp(p: Preferences) = AppSettings(
        protectionEnabled = p[Keys.PROTECTION] ?: true,
        themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
        uninstallProtection = p[Keys.UNINSTALL_PROTECT] ?: false,
        settingsPinHash = p[Keys.PIN_HASH] ?: "",
        settingsPinEnabled = p[Keys.PIN_ENABLED] ?: false
    )

    private fun decodeAi(p: Preferences) = AiSettings(
        sensitivity = p[Keys.AI_SENSITIVITY] ?: 0.55f,
        debounceFrames = p[Keys.AI_DEBOUNCE_FRAMES] ?: 3,
        debounceWindowMs = p[Keys.AI_DEBOUNCE_WINDOW] ?: 4_000L,
        perAppBoost = decodeBoosts(p[Keys.AI_PER_APP_BOOST]),
        contentSourcePackages = decodePackages(p[Keys.AI_SOURCES])
            .ifEmpty { AiSettings.DEFAULT_CONTENT_SOURCES },
        heuristicEnabled = p[Keys.AI_HEURISTIC] ?: true,
        minImageSize = (p[Keys.AI_MIN_IMAGE_SIZE] ?: 120).coerceIn(50, 500),
        // v3.1.3 FIX: default flipped false → true. The DataStore default was
        // overriding the AiSettings data-class default (which v3.1.2 had already
        // flipped to true). Result: even on a fresh install the pre-processor
        // was sending raw [0,255] values to MobileNetV2-class models that expect
        // [0,1], producing near-zero scores → every frame appeared SAFE → "AI
        // doesn't detect NSFW". This restores the [0,1] normalisation by default.
        modelInputNormalized = p[Keys.AI_INPUT_NORMALIZED] ?: true
    )

    private fun decodeBoosts(s: String?): Map<String, Float> {
        if (s.isNullOrBlank()) return emptyMap()
        return s.split(',').mapNotNull { tok ->
            val parts = tok.split('=')
            if (parts.size == 2) {
                val v = parts[1].toFloatOrNull() ?: return@mapNotNull null
                parts[0] to v
            } else null
        }.toMap()
    }

    private fun encodeBoosts(m: Map<String, Float>): String =
        m.entries.joinToString(",") { (k, v) -> "$k=$v" }

    private fun decodePackages(s: String?): Set<String> =
        s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
