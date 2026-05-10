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
 * v2.1.8 fixes:
 *  - `readAppOnce` / `readAiOnce` previously called `dataStore.edit{}` just to
 *    read — that triggered a write on every read. Now uses `.first()`.
 *  - `updateApp` / `updateAi` previously used a read-then-write pair which is
 *    a TOCTOU race. The transform now runs inside a single `edit{}` block, so
 *    concurrent updates are serialized correctly.
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

        val AI_SENSITIVITY = floatPreferencesKey("ai_sensitivity")
        val AI_DEBOUNCE_FRAMES = intPreferencesKey("ai_debounce_frames")
        val AI_DEBOUNCE_WINDOW = longPreferencesKey("ai_debounce_window_ms")
        val AI_PER_APP_BOOST = stringPreferencesKey("ai_per_app_boost_csv") // pkg=boost,...
        val AI_SOURCES = stringPreferencesKey("ai_content_sources_csv")
        val AI_ENGINE = stringPreferencesKey("ai_engine")
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
            p[Keys.AI_ENGINE] = next.engine
        }
    }

    private fun decodeApp(p: Preferences) = AppSettings(
        protectionEnabled = p[Keys.PROTECTION] ?: true,
        themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
        uninstallProtection = p[Keys.UNINSTALL_PROTECT] ?: false
    )

    private fun decodeAi(p: Preferences) = AiSettings(
        sensitivity = p[Keys.AI_SENSITIVITY] ?: 0.55f,
        debounceFrames = p[Keys.AI_DEBOUNCE_FRAMES] ?: 3,
        debounceWindowMs = p[Keys.AI_DEBOUNCE_WINDOW] ?: 4_000L,
        perAppBoost = decodeBoosts(p[Keys.AI_PER_APP_BOOST]),
        contentSourcePackages = decodePackages(p[Keys.AI_SOURCES])
            .ifEmpty { AiSettings.DEFAULT_CONTENT_SOURCES },
        engine = p[Keys.AI_ENGINE] ?: "stub"
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
