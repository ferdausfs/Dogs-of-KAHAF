package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "guardian_prefs")

@Singleton
class GuardianPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_PROTECTION_ENABLED   = booleanPreferencesKey("protection_enabled")
        val KEY_AI_DETECTION_ON      = booleanPreferencesKey("ai_detection_enabled")
        // FIX: spacing fixed
        val KEY_KEYWORD_DETECTION_ON = booleanPreferencesKey("keyword_detection_enabled")
        val KEY_STRICT_MODE          = booleanPreferencesKey("strict_mode")
        val KEY_DELAY_UNLOCK_SECS    = intPreferencesKey("delay_unlock_seconds")
        val KEY_FIRST_RUN            = booleanPreferencesKey("first_run")
        val KEY_AI_THRESHOLD         = floatPreferencesKey("ai_threshold")
        val KEY_AI_INTERVAL_MS       = longPreferencesKey("ai_interval_ms")

        private const val DEFAULT_THRESHOLD   = 0.30f
        private const val DEFAULT_INTERVAL_MS = 600L
        private const val MIN_INTERVAL_MS     = 400L
        private const val MAX_INTERVAL_MS     = 5_000L
    }

    // FIX: Specific error messages per key for easier debugging
    val isProtectionEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_PROTECTION_ENABLED")
            emit(emptyPreferences())
        }
        .map { it[KEY_PROTECTION_ENABLED] ?: true }

    val isAiDetectionEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_AI_DETECTION_ON")
            emit(emptyPreferences())
        }
        .map { it[KEY_AI_DETECTION_ON] ?: false }

    val isKeywordDetectionEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_KEYWORD_DETECTION_ON")
            emit(emptyPreferences())
        }
        .map { it[KEY_KEYWORD_DETECTION_ON] ?: true }

    val isStrictMode: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_STRICT_MODE")
            emit(emptyPreferences())
        }
        .map { it[KEY_STRICT_MODE] ?: false }

    val delayUnlockSeconds: Flow<Int> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_DELAY_UNLOCK_SECS")
            emit(emptyPreferences())
        }
        // FIX: coerceIn only on read — removed double coerceIn
        .map { (it[KEY_DELAY_UNLOCK_SECS] ?: 30).coerceIn(10, 300) }

    val isFirstRun: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_FIRST_RUN")
            emit(emptyPreferences())
        }
        .map { it[KEY_FIRST_RUN] ?: true }

    val aiThreshold: Flow<Float> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_AI_THRESHOLD")
            emit(emptyPreferences())
        }
        .map { it[KEY_AI_THRESHOLD] ?: DEFAULT_THRESHOLD }

    val aiIntervalMs: Flow<Long> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "DataStore read failed: KEY_AI_INTERVAL_MS")
            emit(emptyPreferences())
        }
        .map { (it[KEY_AI_INTERVAL_MS] ?: DEFAULT_INTERVAL_MS)
            .coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS) }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PROTECTION_ENABLED] = enabled }
    }
    suspend fun setAiDetection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AI_DETECTION_ON] = enabled }
    }
    suspend fun setKeywordDetection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEYWORD_DETECTION_ON] = enabled }
    }
    suspend fun setStrictMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STRICT_MODE] = enabled }
    }
    suspend fun setDelayUnlockSeconds(secs: Int) {
        // FIX: coerceIn only on write — read already has coerceIn
        context.dataStore.edit { it[KEY_DELAY_UNLOCK_SECS] = secs.coerceIn(10, 300) }
    }
    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[KEY_FIRST_RUN] = false }
    }
    suspend fun setAiThreshold(v: Float) {
        context.dataStore.edit { it[KEY_AI_THRESHOLD] = v.coerceIn(0.10f, 0.90f) }
    }
    suspend fun setAiIntervalMs(ms: Long) {
        context.dataStore.edit {
            it[KEY_AI_INTERVAL_MS] = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        }
    }
}