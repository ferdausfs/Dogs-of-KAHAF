package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val ds = context.dataStore

    object Keys {
        val KEYWORD_FILTER = booleanPreferencesKey("keyword_filter")
        val AI_DETECTION = booleanPreferencesKey("ai_detection")
        val DELAY_SECONDS = intPreferencesKey("delay_seconds")
        val AI_THRESHOLD = floatPreferencesKey("ai_threshold")
        val FIRST_RUN = booleanPreferencesKey("first_run")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val RULES_VERSION = intPreferencesKey("rules_version")
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    }

    val keywordFilter: Flow<Boolean> = ds.data.map { it[Keys.KEYWORD_FILTER] ?: true }
    val aiDetection: Flow<Boolean> = ds.data.map { it[Keys.AI_DETECTION] ?: false }
    val delaySeconds: Flow<Int> = ds.data.map { it[Keys.DELAY_SECONDS] ?: 30 }
    val aiThreshold: Flow<Float> = ds.data.map { it[Keys.AI_THRESHOLD] ?: 0.7f }
    val firstRun: Flow<Boolean> = ds.data.map { it[Keys.FIRST_RUN] ?: true }
    val userGender: Flow<String> = ds.data.map { it[Keys.USER_GENDER] ?: "NONE" }
    val rulesVersion: Flow<Int> = ds.data.map { it[Keys.RULES_VERSION] ?: 0 }
    val protectionEnabled: Flow<Boolean> = ds.data.map { it[Keys.PROTECTION_ENABLED] ?: true }

    suspend fun setKeywordFilter(v: Boolean) { ds.edit { it[Keys.KEYWORD_FILTER] = v } }
    suspend fun setAiDetection(v: Boolean) { ds.edit { it[Keys.AI_DETECTION] = v } }
    suspend fun setDelaySeconds(v: Int) { ds.edit { it[Keys.DELAY_SECONDS] = v } }
    suspend fun setAiThreshold(v: Float) { ds.edit { it[Keys.AI_THRESHOLD] = v } }
    suspend fun setFirstRun(v: Boolean) { ds.edit { it[Keys.FIRST_RUN] = v } }
    suspend fun setUserGender(v: String) { ds.edit { it[Keys.USER_GENDER] = v } }
    suspend fun bumpRulesVersion() { ds.edit { it[Keys.RULES_VERSION] = (it[Keys.RULES_VERSION] ?: 0) + 1 } }
    suspend fun setProtectionEnabled(v: Boolean) { ds.edit { it[Keys.PROTECTION_ENABLED] = v } }
}
