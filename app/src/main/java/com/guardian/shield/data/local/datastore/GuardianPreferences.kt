package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

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

        // ── Opposite-gender NSFW filter ────────────────────────────────────
        // "MALE"   → block FEMALE NSFW
        // "FEMALE" → block MALE NSFW
        // "NONE"   → feature disabled (default)
        val KEY_USER_GENDER    = stringPreferencesKey("user_gender")

        const val GENDER_MALE   = "MALE"
        const val GENDER_FEMALE = "FEMALE"
        const val GENDER_NONE   = "NONE"
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

    /** User-selected gender. Default = NONE → feature is OFF. */
    val userGender: Flow<String> =
        context.dataStore.data.map { it[KEY_USER_GENDER] ?: GENDER_NONE }

    suspend fun setKeywordFilter(v: Boolean) = context.dataStore.edit { it[KEY_KEYWORD_FILTER] = v }
    suspend fun setAiDetection(v: Boolean)   = context.dataStore.edit { it[KEY_AI_DETECTION] = v }
    suspend fun setDelaySeconds(v: Int)      = context.dataStore.edit { it[KEY_DELAY_SECONDS] = v }
    suspend fun setAiThreshold(v: Float)     = context.dataStore.edit { it[KEY_AI_THRESHOLD] = v }
    suspend fun setFirstRun(v: Boolean)      = context.dataStore.edit { it[KEY_FIRST_RUN] = v }

    /** Persist user gender. Pass [GENDER_MALE], [GENDER_FEMALE], or [GENDER_NONE]. */
    suspend fun setUserGender(v: String) = context.dataStore.edit {
        it[KEY_USER_GENDER] = when (v) {
            GENDER_MALE, GENDER_FEMALE, GENDER_NONE -> v
            else -> GENDER_NONE
        }
    }
}
