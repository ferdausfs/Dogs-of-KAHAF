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
    @ApplicationContext private val context: Context  // ✅ @ApplicationContext যোগ
) {

    companion object {
        val KEY_KEYWORD_FILTER = booleanPreferencesKey("keyword_filter")
        val KEY_AI_DETECTION   = booleanPreferencesKey("ai_detection")
        val KEY_DELAY_SECONDS  = intPreferencesKey("delay_seconds")
        val KEY_AI_THRESHOLD   = floatPreferencesKey("ai_threshold")
        val KEY_FIRST_RUN      = booleanPreferencesKey("first_run")
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

    suspend fun setKeywordFilter(v: Boolean) = context.dataStore.edit { it[KEY_KEYWORD_FILTER] = v }
    suspend fun setAiDetection(v: Boolean) = context.dataStore.edit { it[KEY_AI_DETECTION] = v }
    suspend fun setDelaySeconds(v: Int) = context.dataStore.edit { it[KEY_DELAY_SECONDS] = v }
    suspend fun setAiThreshold(v: Float) = context.dataStore.edit { it[KEY_AI_THRESHOLD] = v }
    suspend fun setFirstRun(v: Boolean) = context.dataStore.edit { it[KEY_FIRST_RUN] = v }
}