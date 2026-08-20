package com.guardian.shield.backup

import android.content.Context
import android.net.Uri
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.db.AppRuleDao
import com.guardian.shield.data.local.db.AppRuleEntity
import com.guardian.shield.data.local.db.KeywordDao
import com.guardian.shield.data.local.db.KeywordRuleEntity
import com.guardian.shield.data.local.db.ScheduleRuleDao
import com.guardian.shield.data.local.db.ScheduleRuleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 4a (v3.5.0) — local backup/restore for RULES + SETTINGS.
 *
 * Scope is deliberate and documented (both here and in the report):
 *  INCLUDED   — app rules, keyword rules, schedule rules, user-tunable
 *               settings (keyword filter, AI detection, unlock delay,
 *               temp-block duration, AI threshold, grid votes, master
 *               protection toggle), accountability partner contact.
 *  EXCLUDED   — block_events / pending_reports (detection history is not a
 *               "setting"), Time-Lock state and PIN + recovery code
 *               (commitment devices: a restore must never silently clear or
 *               weaken them — they live in SecureStorage and are untouched),
 *               false-positive learned signatures (detection data).
 *
 * Storage: a plain JSON file the user picks via SAF (CreateDocument /
 * OpenDocument). No cloud, no network permission — the file lives wherever
 * the user put it and is lost with uninstall unless they saved a copy.
 *
 * Import semantics: keywords + settings are REPLACED to match the backup;
 * app and schedule rules are MERGED (upsert) so a partial backup never wipes
 * rules it doesn't know about. Unknown JSON keys are ignored (forward-
 * compatible); out-of-range values are clamped to the same bounds the
 * settings UI sliders enforce.
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppRuleDao,
    private val keywordDao: KeywordDao,
    private val scheduleDao: ScheduleRuleDao,
    private val prefs: GuardianPreferences
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_TAG = "guardian_shield_backup"
        const val SUGGESTED_MIME = "application/json"

        // Same bounds as the settings sliders (activity_settings.xml).
        private const val DELAY_MIN = 5
        private const val DELAY_MAX = 120
        private const val THRESHOLD_MIN = 0.30f
        private const val THRESHOLD_MAX = 0.95f
        private val TEMP_BLOCK_CHOICES = intArrayOf(15, 30, 60)
        private const val VOTES_MIN = 1
        private const val VOTES_MAX = 4
    }

    /** Thrown on any unreadable/foreign/corrupt file — message is user-safe. */
    class BackupException(message: String) : Exception(message)

    data class ImportResult(
        val apps: Int,
        val keywords: Int,
        val schedules: Int,
        val settingsApplied: Boolean,
        val partnerApplied: Boolean
    )

    // ---------------------------------------------------------------- export

    suspend fun exportTo(uri: Uri, appVersion: String): Unit = withContext(Dispatchers.IO) {
        val json = buildJson(appVersion)
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        if (!ok) throw BackupException("write-failed")
    }

    private suspend fun buildJson(appVersion: String): String {
        val root = JSONObject()
        root.put(FILE_TAG, true)
        root.put("format_version", FORMAT_VERSION)
        root.put("exported_at", System.currentTimeMillis())
        root.put("app_version", appVersion)

        val apps = JSONArray()
        for (a in appDao.getAll()) {
            apps.put(JSONObject().apply {
                put("packageName", a.packageName)
                put("appName", a.appName)
                put("isBlocked", a.isBlocked)
                put("isWhitelisted", a.isWhitelisted)
                put("createdAt", a.createdAt)
            })
        }
        root.put("app_rules", apps)

        val keywords = JSONArray()
        for (k in keywordDao.getAll()) {
            keywords.put(JSONObject().apply {
                put("keyword", k.keyword)
                put("isRegex", k.isRegex)
                put("severity", k.severity)
                put("enabled", k.enabled)
            })
        }
        root.put("keyword_rules", keywords)

        val schedules = JSONArray()
        for (s in scheduleDao.getAll()) {
            schedules.put(JSONObject().apply {
                put("packageName", s.packageName)
                put("startHour", s.startHour)
                put("startMinute", s.startMinute)
                put("endHour", s.endHour)
                put("endMinute", s.endMinute)
                put("enabledDaysMask", s.enabledDaysMask)
                put("enabled", s.enabled)
                put("createdAt", s.createdAt)
            })
        }
        root.put("schedule_rules", schedules)

        root.put("settings", JSONObject().apply {
            put("keyword_filter", prefs.keywordFilter.first())
            put("ai_detection", prefs.aiDetection.first())
            put("delay_seconds", prefs.delaySeconds.first())
            put("temp_block_duration_mins", prefs.tempBlockDurationMins.first())
            put("ai_threshold", prefs.aiThreshold.first().toDouble())
            put("grid_vote_count", prefs.gridVoteCount.first())
            put("protection_enabled", prefs.protectionEnabled.first())
        })

        root.put("partner", JSONObject().apply {
            put("name", prefs.partnerName.first())
            put("email", prefs.partnerEmail.first())
        })

        return root.toString(2)
    }

    // ---------------------------------------------------------------- import

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: throw BackupException("read-failed")
        if (text.length > 2_000_000) throw BackupException("too-large")

        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw BackupException("not-json")
        if (!root.optBoolean(FILE_TAG, false) ||
            root.optInt("format_version", -1) != FORMAT_VERSION
        ) throw BackupException("not-a-backup")

        val apps = importApps(root.optJSONArray("app_rules"))
        val keywords = importKeywords(root.optJSONArray("keyword_rules"))
        val schedules = importSchedules(root.optJSONArray("schedule_rules"))
        val settingsApplied = importSettings(root.optJSONObject("settings"))
        val partnerApplied = importPartner(root.optJSONObject("partner"))

        if (!settingsApplied && apps == 0 && keywords == 0 && schedules == 0) {
            throw BackupException("empty-backup")
        }
        ImportResult(apps, keywords, schedules, settingsApplied, partnerApplied)
    }

    private suspend fun importApps(arr: JSONArray?): Int {
        if (arr == null) return 0
        val rows = ArrayList<AppRuleEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("packageName", "").trim()
            if (pkg.isEmpty()) continue
            rows += AppRuleEntity(
                packageName = pkg,
                appName = o.optString("appName", pkg),
                isBlocked = o.optBoolean("isBlocked", false),
                isWhitelisted = o.optBoolean("isWhitelisted", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
        if (rows.isNotEmpty()) appDao.upsertAll(rows)   // merge, never wipe
        return rows.size
    }

    private suspend fun importKeywords(arr: JSONArray?): Int {
        if (arr == null) return 0
        val rows = ArrayList<KeywordRuleEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kw = o.optString("keyword", "").trim()
            if (kw.isEmpty()) continue
            rows += KeywordRuleEntity(
                id = 0,   // regenerate ids — never trust ids from a file
                keyword = kw,
                isRegex = o.optBoolean("isRegex", false),
                severity = o.optInt("severity", 1).coerceIn(1, 3),
                enabled = o.optBoolean("enabled", true)
            )
        }
        // Keywords are the full replacement case: backup == desired state.
        keywordDao.clear()
        for (k in rows) keywordDao.upsert(k)
        return rows.size
    }

    private suspend fun importSchedules(arr: JSONArray?): Int {
        if (arr == null) return 0
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("packageName", "").trim()
            if (pkg.isEmpty()) continue
            scheduleDao.upsert(
                ScheduleRuleEntity(
                    packageName = pkg,
                    startHour = o.optInt("startHour", 0).coerceIn(0, 23),
                    startMinute = o.optInt("startMinute", 0).coerceIn(0, 59),
                    endHour = o.optInt("endHour", 0).coerceIn(0, 23),
                    endMinute = o.optInt("endMinute", 0).coerceIn(0, 59),
                    enabledDaysMask = o.optInt("enabledDaysMask", 0x7F) and 0x7F,
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
            count++
        }
        return count
    }

    private suspend fun importSettings(o: JSONObject?): Boolean {
        if (o == null) return false
        if (o.has("keyword_filter")) prefs.setKeywordFilter(o.optBoolean("keyword_filter", true))
        if (o.has("ai_detection")) prefs.setAiDetection(o.optBoolean("ai_detection", false))
        if (o.has("delay_seconds")) {
            prefs.setDelaySeconds(o.optInt("delay_seconds", 30).coerceIn(DELAY_MIN, DELAY_MAX))
        }
        if (o.has("temp_block_duration_mins")) {
            val v = o.optInt("temp_block_duration_mins", 15)
            prefs.setTempBlockDurationMins(
                TEMP_BLOCK_CHOICES.minByOrNull { kotlin.math.abs(it - v) } ?: 15
            )
        }
        if (o.has("ai_threshold")) {
            prefs.setAiThreshold(
                o.optDouble("ai_threshold", 0.72).toFloat().coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
            )
        }
        if (o.has("grid_vote_count")) {
            prefs.setGridVoteCount(o.optInt("grid_vote_count", 2).coerceIn(VOTES_MIN, VOTES_MAX))
        }
        if (o.has("protection_enabled")) {
            prefs.setProtectionEnabled(o.optBoolean("protection_enabled", true))
        }
        return true
    }

    private suspend fun importPartner(o: JSONObject?): Boolean {
        if (o == null) return false
        val email = o.optString("email", "").trim()
        val name = o.optString("name", "").trim()
        if (email.isNotEmpty()) prefs.setPartner(name, email)
        return email.isNotEmpty()
    }
}
