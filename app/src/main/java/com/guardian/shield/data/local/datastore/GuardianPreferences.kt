package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

/**
 * v9 (2.0.0):
 *  • P4-C → KEY_PROTECTION_ENABLED master switch (default true). The
 *    AccessibilityService skips all processing when this is false, allowing
 *    a quick FAB toggle from the dashboard without going into Settings.
 *
 *  Earlier v8 BUG-12 KEY_RULES_VERSION counter preserved.
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

        // P4-C: master protection switch (FAB quick toggle on dashboard).
        // Default true to preserve existing behaviour for upgraded installs.
        val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    }

    val keywordFilterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_KEYWORD_FILTER] ?: true }
    val aiDetectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AI_DETECTION] ?: false }
    val delaySeconds: Flow<Int> =
        context.dataStore.data.map { it[KEY_DELAY_SECONDS] ?: 30 }
    val aiThreshold: Flow<Float> =
        context.dataStore.data.map { it[KEY_AI_THRESHOLD] ?: 0.7f }
    val isFirstRun: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FIRST_RUN] ?: true }

    val userGender: Flow<String> =
        context.dataStore.data.map { it[KEY_USER_GENDER] ?: GENDER_NONE }

    val rulesVersion: Flow<Int> =
        context.dataStore.data.map { it[KEY_RULES_VERSION] ?: 0 }

    /** P4-C: master protection switch. Default true. */
    val protectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PROTECTION_ENABLED] ?: true }

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

    /** P4-C: snapshot read for guard checks in the AccessibilityService. */
    suspend fun currentProtectionEnabled(): Boolean = protectionEnabled.first()

    /** P4-C: persist the master protection switch. */
    suspend fun setProtectionEnabled(v: Boolean) = context.dataStore.edit {
        it[KEY_PROTECTION_ENABLED] = v
    }
}
