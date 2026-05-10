package com.guardian.shield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

/**
 * v12 (2.1.2):
 *  • CRITICAL FIX: currentRulesVersion() / currentProtectionEnabled() now
 *    use withTimeoutOrNull(2s) around .first(). Previously, on a freshly
 *    installed / corrupted DataStore the Flow could *never emit* and the
 *    suspending caller hung forever — manifested to the user as a frozen
 *    settings screen.
 *  • DEFENSIVE: every Flow now also catches generic Throwable (some
 *    ROMs throw NPE from PreferencesProto deserialisation).
 *
 * v11 (2.1.1):
 *  • Every Flow has .catch{} that swallows IOException and emits empty.
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

        val KEY_USER_GENDER    = stringPreferencesKey("user_gender")

        const val GENDER_MALE   = "MALE"
        const val GENDER_FEMALE = "FEMALE"
        const val GENDER_NONE   = "NONE"

        val KEY_RULES_VERSION  = intPreferencesKey("rules_version")
        val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")

        // v12: max time we'll wait for a single .first() read before
        // giving up and using a default. Prevents UI hang on broken
        // DataStore.
        private const val FIRST_READ_TIMEOUT_MS = 2_000L
    }

    private fun safeData(): Flow<Preferences> = context.dataStore.data.catch { e ->
        when (e) {
            is IOException -> {
                Timber.w(e, "DataStore read failed (IO) — emitting empty preferences")
                emit(emptyPreferences())
            }
            else -> {
                // v12: also swallow non-IO throwables — some OEMs throw
                // NPE / IllegalStateException from PreferencesProto.
                Timber.w(e, "DataStore read failed (non-IO) — emitting empty preferences")
                emit(emptyPreferences())
            }
        }
    }

    val keywordFilterEnabled: Flow<Boolean> =
        safeData().map { it[KEY_KEYWORD_FILTER] ?: true }
    val aiDetectionEnabled: Flow<Boolean> =
        safeData().map { it[KEY_AI_DETECTION] ?: false }
    val delaySeconds: Flow<Int> =
        safeData().map { it[KEY_DELAY_SECONDS] ?: 30 }

    val aiThreshold: Flow<Float> =
        safeData().map { it[KEY_AI_THRESHOLD] ?: GuardianConstants.DEFAULT_AI_THRESHOLD }

    val isFirstRun: Flow<Boolean> =
        safeData().map { it[KEY_FIRST_RUN] ?: true }

    val userGender: Flow<String> =
        safeData().map { it[KEY_USER_GENDER] ?: GENDER_NONE }

    val rulesVersion: Flow<Int> =
        safeData().map { it[KEY_RULES_VERSION] ?: 0 }

    val protectionEnabled: Flow<Boolean> =
        safeData().map { it[KEY_PROTECTION_ENABLED] ?: true }

    val sensitivity: Flow<String> = safeData().map {
        it[KEY_SENSITIVITY] ?: GuardianConstants.SENSITIVITY_BALANCED
    }

    suspend fun setKeywordFilter(v: Boolean) = safeEdit { it[KEY_KEYWORD_FILTER] = v }
    suspend fun setAiDetection(v: Boolean)   = safeEdit { it[KEY_AI_DETECTION] = v }
    suspend fun setDelaySeconds(v: Int)      = safeEdit { it[KEY_DELAY_SECONDS] = v }
    suspend fun setAiThreshold(v: Float)     = safeEdit { it[KEY_AI_THRESHOLD] = v }
    suspend fun setFirstRun(v: Boolean)      = safeEdit { it[KEY_FIRST_RUN] = v }

    suspend fun setUserGender(v: String) = safeEdit {
        it[KEY_USER_GENDER] = when (v) {
            GENDER_MALE, GENDER_FEMALE, GENDER_NONE -> v
            else -> GENDER_NONE
        }
    }

    /**
     * v12: bounded-wait first read. If DataStore never emits within 2 s
     * we return 0 instead of suspending forever.
     */
    suspend fun currentRulesVersion(): Int =
        runCatching {
            withTimeoutOrNull(FIRST_READ_TIMEOUT_MS) { rulesVersion.first() } ?: 0
        }.getOrDefault(0)

    suspend fun bumpRulesVersion() = safeEdit {
        val curr = it[KEY_RULES_VERSION] ?: 0
        it[KEY_RULES_VERSION] = curr + 1
    }

    suspend fun currentProtectionEnabled(): Boolean =
        runCatching {
            withTimeoutOrNull(FIRST_READ_TIMEOUT_MS) { protectionEnabled.first() } ?: true
        }.getOrDefault(true)

    suspend fun setProtectionEnabled(v: Boolean) = safeEdit {
        it[KEY_PROTECTION_ENABLED] = v
    }

    suspend fun setSensitivity(level: String) = safeEdit {
        it[KEY_SENSITIVITY] = when (level) {
            GuardianConstants.SENSITIVITY_LOW,
            GuardianConstants.SENSITIVITY_BALANCED,
            GuardianConstants.SENSITIVITY_HIGH -> level
            else -> GuardianConstants.SENSITIVITY_BALANCED
        }
    }

    private suspend fun safeEdit(block: (MutablePreferences) -> Unit) {
        runCatching { context.dataStore.edit(block) }
            .onFailure { Timber.w(it, "DataStore edit failed (suppressed)") }
    }
}
