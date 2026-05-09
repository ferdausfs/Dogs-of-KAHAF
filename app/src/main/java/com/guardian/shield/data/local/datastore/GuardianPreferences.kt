package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

/**
 * v10 (2.1.0):
 *  • KEY_SENSITIVITY ("LOW" / "BALANCED" / "HIGH") — high-level preset
 *    that drives the effective AI threshold. Default BALANCED (0.78).
 *  • Default ai_threshold raised 0.7 → 0.78 (BALANCED preset).
 *
 * v9 (2.0.0):
 *  • KEY_PROTECTION_ENABLED master switch.
 *  • KEY_RULES_VERSION counter.
 */
@Singleton
class GuardianPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val KEY_KEYWORD_FILTER = booleanPreferencesKey("keyword_filter")
        val KEY_AI_DETECTION   = booleanPreferencesKey("ai_detection")
        val KEY_DELAY_SECONDS  = intPreferencesKey("delay_seconds")
        val KEY_AI_THRESHOLD   = floatPreferencesKey("ai_threshold")
        val KEY_FIRST_RUN      = booleanPreferencesKey("first_run")

        // Opposite-gender NSFW filter
        val KEY_USER_GENDER    = stringPreferencesKey("user_gender")

        const val GENDER_MALE   = "MALE"
        const val GENDER_FEMALE = "FEMALE"
        const val GENDER_NONE   = "NONE"

        // BUG-12: monotonically-increasing rules version counter.
        val KEY_RULES_VERSION  = intPreferencesKey("rules_version")

        // P4-C: master protection switch.
        val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")

        // v10 (2.1.0): sensitivity preset.
        val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
    }

    val keywordFilterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_KEYWORD_FILTER] ?: true }
    val aiDetectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AI_DETECTION] ?: false }
    val delaySeconds: Flow<Int> =
        context.dataStore.data.map { it[KEY_DELAY_SECONDS] ?: 30 }

    /** v10: default raised 0.7 → 0.78 to match the BALANCED sensitivity preset. */
    val aiThreshold: Flow<Float> =
        context.dataStore.data.map { it[KEY_AI_THRESHOLD] ?: GuardianConstants.DEFAULT_AI_THRESHOLD }

    val isFirstRun: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FIRST_RUN] ?: true }

    val userGender: Flow<String> =
        context.dataStore.data.map { it[KEY_USER_GENDER] ?: GENDER_NONE }

    val rulesVersion: Flow<Int> =
        context.dataStore.data.map { it[KEY_RULES_VERSION] ?: 0 }

    val protectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PROTECTION_ENABLED] ?: true }

    /** v10: sensitivity preset. Default BALANCED. */
    val sensitivity: Flow<String> = context.dataStore.data.map {
        it[KEY_SENSITIVITY] ?: GuardianConstants.SENSITIVITY_BALANCED
    }

    suspend fun setKeywordFilter(v: Boolean) = context.dataStore.edit { it[KEY_KEYWORD_FILTER] = v }
    suspend fun setAiDetection(v: Boolean)   = context.dataStore.edit { it[KEY_AI_DETECTION] = v }
    suspend fun setDelaySeconds(v: Int)      = context.dataStore.edit { it[KEY_DELAY_SECONDS] = v }
    suspend fun setAiThreshold(v: Float)     = context.dataStore.edit { it[KEY_AI_THRESHOLD] = v }
    suspend fun setFirstRun(v: Boolean)      = context.dataStore.edit { it[KEY_FIRST_RUN] = v }

    suspend fun setUserGender(v: String) = context.dataStore.edit {
        it[KEY_USER_GENDER] = when (v) {
            GENDER_MALE, GENDER_FEMALE, GENDER_NONE -> v
            else -> GENDER_NONE
        }
    }

    suspend fun currentRulesVersion(): Int = rulesVersion.first()

    suspend fun bumpRulesVersion() = context.dataStore.edit {
        val curr = it[KEY_RULES_VERSION] ?: 0
        it[KEY_RULES_VERSION] = curr + 1
    }

    suspend fun currentProtectionEnabled(): Boolean = protectionEnabled.first()

    suspend fun setProtectionEnabled(v: Boolean) = context.dataStore.edit {
        it[KEY_PROTECTION_ENABLED] = v
    }

    /** v10: persist the sensitivity preset. */
    suspend fun setSensitivity(level: String) = context.dataStore.edit {
        it[KEY_SENSITIVITY] = when (level) {
            GuardianConstants.SENSITIVITY_LOW,
            GuardianConstants.SENSITIVITY_BALANCED,
            GuardianConstants.SENSITIVITY_HIGH -> level
            else -> GuardianConstants.SENSITIVITY_BALANCED
        }
    }
}
