package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        val FIRST_RUN = booleanPreferencesKey("first_run")
        val RULES_VERSION = intPreferencesKey("rules_version")
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val TEMP_BLOCK_DURATION_MINS = intPreferencesKey("temp_block_duration_mins")

        // AI Threshold — DataStore এ
        val AI_THRESHOLD = floatPreferencesKey("ai_threshold")
        val GRID_VOTE_COUNT = intPreferencesKey("grid_vote_count")

        // PHASE 2 (v3.5.0) — accountability partner (contact is stored locally
        // only; nothing is transmitted by the app itself).
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val PARTNER_EMAIL = stringPreferencesKey("partner_email")

        // PHASE 4c (v3.5.0) — notification shade shield. OFF by default; only
        // takes effect after the user ALSO grants system Notification access.
        val NOTIF_SHIELD_ENABLED = booleanPreferencesKey("notif_shield_enabled")

        // R4 — Focus Mode (temporary full-device pause of distracting apps).
        // Until-timestamp in ms (0 = inactive) + the duration chosen when the
        // session started (so the countdown ring can show elapsed fraction
        // even after process death).
        val FOCUS_UNTIL_MS = longPreferencesKey("focus_until_ms")
        val FOCUS_DURATION_MINS = intPreferencesKey("focus_duration_mins")

        // R4 — Smart Filters: ids of enabled keyword categories (presets live
        // in util/FilterCategories; the actual KeywordRule rows are materialized
        // into keyword_rules so RulesEngine needs no schema change).
        val FILTER_CATEGORIES = stringSetPreferencesKey("filter_categories")

        // R5 — Private DNS auto-schedule ("night shield"). One DAILY window
        // (minutes-of-day, overnight-safe: start > end wraps midnight) during
        // which a filtered DoT hostname is applied as the device Private DNS;
        // the user's own previous DNS setting is restored outside the window.
        val DNS_AUTO_ENABLED = booleanPreferencesKey("dns_auto_enabled")
        val DNS_AUTO_START_MIN = intPreferencesKey("dns_auto_start_min")
        val DNS_AUTO_END_MIN = intPreferencesKey("dns_auto_end_min")
        val DNS_AUTO_HOST = stringPreferencesKey("dns_auto_host")

        // R7.5 — Bedtime Mode: nightly scheduled Focus (minutes-of-day,
        // overnight-safe) that extends FOCUS_UNTIL_MS to the window end.
        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_START_MIN = intPreferencesKey("bedtime_start_min")
        val BEDTIME_END_MIN = intPreferencesKey("bedtime_end_min")
    }

    // Flows
    val keywordFilter: Flow<Boolean> = ds.data.map { it[Keys.KEYWORD_FILTER] ?: true }
    val aiDetection: Flow<Boolean> = ds.data.map { it[Keys.AI_DETECTION] ?: false }
    val delaySeconds: Flow<Int> = ds.data.map { it[Keys.DELAY_SECONDS] ?: 30 }
    val firstRun: Flow<Boolean> = ds.data.map { it[Keys.FIRST_RUN] ?: true }
    val rulesVersion: Flow<Int> = ds.data.map { it[Keys.RULES_VERSION] ?: 0 }
    val protectionEnabled: Flow<Boolean> = ds.data.map { it[Keys.PROTECTION_ENABLED] ?: true }
    val tempBlockDurationMins: Flow<Int> = ds.data.map { it[Keys.TEMP_BLOCK_DURATION_MINS] ?: 15 }

    // ✅ AI Threshold flows — সব adjustable
    val aiThreshold: Flow<Float> = ds.data.map { it[Keys.AI_THRESHOLD] ?: 0.72f }
    val gridVoteCount: Flow<Int> = ds.data.map { it[Keys.GRID_VOTE_COUNT] ?: 2 }

    // PHASE 2 (v3.5.0) — accountability partner
    val partnerName: Flow<String> = ds.data.map { it[Keys.PARTNER_NAME] ?: "" }
    val partnerEmail: Flow<String> = ds.data.map { it[Keys.PARTNER_EMAIL] ?: "" }

    // PHASE 4c (v3.5.0) — notification shade shield (default OFF)
    val notifShieldEnabled: Flow<Boolean> = ds.data.map { it[Keys.NOTIF_SHIELD_ENABLED] ?: false }

    // R4 — Focus Mode state
    val focusUntilMs: Flow<Long> = ds.data.map { it[Keys.FOCUS_UNTIL_MS] ?: 0L }
    val focusDurationMins: Flow<Int> = ds.data.map { it[Keys.FOCUS_DURATION_MINS] ?: 45 }

    // R4 — Smart Filters: enabled category ids
    val filterCategories: Flow<Set<String>> = ds.data.map { it[Keys.FILTER_CATEGORIES] ?: emptySet() }

    // R5 — Private DNS auto-schedule (defaults: 8:00 PM -> 8:00 AM, host unset)
    val dnsAutoEnabled: Flow<Boolean> = ds.data.map { it[Keys.DNS_AUTO_ENABLED] ?: false }
    val dnsAutoStartMin: Flow<Int> = ds.data.map { it[Keys.DNS_AUTO_START_MIN] ?: (20 * 60) }
    val dnsAutoEndMin: Flow<Int> = ds.data.map { it[Keys.DNS_AUTO_END_MIN] ?: (8 * 60) }

    // R7.5 — Bedtime Mode (nightly scheduled focus window)
    val bedtimeEnabled: Flow<Boolean> = ds.data.map { it[Keys.BEDTIME_ENABLED] ?: false }
    val bedtimeStartMin: Flow<Int> = ds.data.map { it[Keys.BEDTIME_START_MIN] ?: (23 * 60) }
    val bedtimeEndMin: Flow<Int> = ds.data.map { it[Keys.BEDTIME_END_MIN] ?: (6 * 60) }
    val dnsAutoHost: Flow<String> = ds.data.map { it[Keys.DNS_AUTO_HOST] ?: "" }

    // Setters
    suspend fun setKeywordFilter(v: Boolean) { ds.edit { it[Keys.KEYWORD_FILTER] = v } }
    suspend fun setAiDetection(v: Boolean) { ds.edit { it[Keys.AI_DETECTION] = v } }
    suspend fun setDelaySeconds(v: Int) { ds.edit { it[Keys.DELAY_SECONDS] = v } }
    suspend fun setFirstRun(v: Boolean) { ds.edit { it[Keys.FIRST_RUN] = v } }
    suspend fun setProtectionEnabled(v: Boolean) { ds.edit { it[Keys.PROTECTION_ENABLED] = v } }
    suspend fun setTempBlockDurationMins(v: Int) { ds.edit { it[Keys.TEMP_BLOCK_DURATION_MINS] = v } }
    suspend fun setAiThreshold(v: Float) { ds.edit { it[Keys.AI_THRESHOLD] = v } }
    suspend fun setGridVoteCount(v: Int) { ds.edit { it[Keys.GRID_VOTE_COUNT] = v } }
    suspend fun bumpRulesVersion() {
        ds.edit { it[Keys.RULES_VERSION] = (it[Keys.RULES_VERSION] ?: 0) + 1 }
    }

    // PHASE 2 (v3.5.0) — accountability partner. Blank email = no partner set.
    suspend fun setPartner(name: String, email: String) {
        ds.edit {
            it[Keys.PARTNER_NAME] = name.trim()
            it[Keys.PARTNER_EMAIL] = email.trim()
        }
    }
    suspend fun clearPartner() = setPartner("", "")

    // PHASE 4c (v3.5.0) — notification shade shield toggle.
    suspend fun setNotifShieldEnabled(v: Boolean) { ds.edit { it[Keys.NOTIF_SHIELD_ENABLED] = v } }

    // R4 — Focus Mode
    suspend fun setFocusUntilMs(v: Long) { ds.edit { it[Keys.FOCUS_UNTIL_MS] = v } }
    suspend fun setFocusDurationMins(v: Int) { ds.edit { it[Keys.FOCUS_DURATION_MINS] = v } }

    // R4 — Smart Filters
    suspend fun setFilterCategories(v: Set<String>) { ds.edit { it[Keys.FILTER_CATEGORIES] = v } }

    // R5 — Private DNS auto-schedule
    suspend fun setDnsAutoEnabled(v: Boolean) { ds.edit { it[Keys.DNS_AUTO_ENABLED] = v } }
    suspend fun setDnsAutoWindow(startMin: Int, endMin: Int) {
        ds.edit {
            it[Keys.DNS_AUTO_START_MIN] = startMin
            it[Keys.DNS_AUTO_END_MIN] = endMin
        }
    }
    suspend fun setDnsAutoHost(v: String) { ds.edit { it[Keys.DNS_AUTO_HOST] = v.trim().lowercase() } }
    suspend fun setDnsAuto(enabled: Boolean, startMin: Int, endMin: Int, host: String) {
        ds.edit {
            it[Keys.DNS_AUTO_ENABLED] = enabled
            it[Keys.DNS_AUTO_START_MIN] = startMin
            it[Keys.DNS_AUTO_END_MIN] = endMin
            it[Keys.DNS_AUTO_HOST] = host.trim().lowercase()
        }
    }

    // R7.5 — Bedtime Mode
    suspend fun setBedtime(enabled: Boolean, startMin: Int, endMin: Int) {
        ds.edit {
            it[Keys.BEDTIME_ENABLED] = enabled
            it[Keys.BEDTIME_START_MIN] = startMin
            it[Keys.BEDTIME_END_MIN] = endMin
        }
    }
}