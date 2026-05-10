package com.kahaf.guardianshield.data.repository

import com.kahaf.guardianshield.data.datastore.SettingsDataStore
import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.ThemeMode
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override val appSettings: Flow<AppSettings> = dataStore.appSettings
    override val aiSettings: Flow<AiSettings> = dataStore.aiSettings

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.updateApp(transform)
    }

    override suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings) {
        dataStore.updateAi(transform)
    }

    /**
     * v3.0.0:
     *  - bumped snapshot version to 2
     *  - removed `aiEngine` (no longer user-selectable)
     *  - added `aiHeuristicEnabled`, `aiMinImageSize`, `aiInputNormalized`
     *  - PIN hash/enabled are intentionally NOT exported (don't move PINs across devices)
     *
     * `ignoreUnknownKeys = true` keeps importing v1 snapshots safe — we just
     * drop the old `aiEngine` key.
     */
    @Serializable
    data class ConfigSnapshot(
        val version: Int = 2,
        val themeMode: String,
        val dynamicColor: Boolean,
        val protectionEnabled: Boolean,
        val uninstallProtection: Boolean,
        val aiSensitivity: Float,
        val aiDebounceFrames: Int,
        val aiDebounceWindowMs: Long,
        val aiPerAppBoost: Map<String, Float>,
        val aiContentSources: List<String>,
        val aiHeuristicEnabled: Boolean = true,
        val aiMinImageSize: Int = 120,
        val aiInputNormalized: Boolean = false
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun exportJson(): String {
        val app = dataStore.appSettings.first()
        val ai = dataStore.aiSettings.first()
        val snap = ConfigSnapshot(
            themeMode = app.themeMode.name,
            dynamicColor = app.dynamicColor,
            protectionEnabled = app.protectionEnabled,
            uninstallProtection = app.uninstallProtection,
            aiSensitivity = ai.sensitivity,
            aiDebounceFrames = ai.debounceFrames,
            aiDebounceWindowMs = ai.debounceWindowMs,
            aiPerAppBoost = ai.perAppBoost,
            aiContentSources = ai.contentSourcePackages.toList(),
            aiHeuristicEnabled = ai.heuristicEnabled,
            aiMinImageSize = ai.minImageSize,
            aiInputNormalized = ai.modelInputNormalized
        )
        return json.encodeToString(snap)
    }

    override suspend fun importJson(jsonStr: String): Boolean {
        val snap = runCatching { json.decodeFromString<ConfigSnapshot>(jsonStr) }
            .getOrElse { return false }
        dataStore.updateApp { current ->
            current.copy(
                themeMode = runCatching { ThemeMode.valueOf(snap.themeMode) }
                    .getOrDefault(current.themeMode),
                dynamicColor = snap.dynamicColor,
                protectionEnabled = snap.protectionEnabled,
                uninstallProtection = snap.uninstallProtection
                // PIN hash/enabled intentionally not imported.
            )
        }
        dataStore.updateAi { current ->
            current.copy(
                sensitivity = snap.aiSensitivity,
                debounceFrames = snap.aiDebounceFrames,
                debounceWindowMs = snap.aiDebounceWindowMs,
                perAppBoost = snap.aiPerAppBoost,
                contentSourcePackages = snap.aiContentSources.toSet(),
                heuristicEnabled = snap.aiHeuristicEnabled,
                minImageSize = snap.aiMinImageSize.coerceIn(50, 500),
                modelInputNormalized = snap.aiInputNormalized
            )
        }
        return true
    }
}
