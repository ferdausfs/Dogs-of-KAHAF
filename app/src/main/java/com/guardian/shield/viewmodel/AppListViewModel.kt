package com.guardian.shield.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.usecase.DeleteAppRuleUseCase
import com.guardian.shield.domain.usecase.GetAppRulesUseCase
import com.guardian.shield.domain.usecase.UpsertAppRuleUseCase
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.util.AppClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

data class InstalledApp(
    val pkg: String,
    val name: String,
    val rule: AppRule?,
    val isSystemApp: Boolean,
    val isAlwaysAllowed: Boolean
)

/**
 * v12 (2.1.2):
 *  • CRITICAL FIX: load() previously called `getRules().first()` from the
 *    Main dispatcher (viewModelScope default). When the underlying Flow
 *    had not emitted yet (cold Flow + Room not yet initialised), .first()
 *    suspended on Main forever → list never appeared and tapping the
 *    screen showed "App not responding".
 *    Fix: the entire load logic now runs inside withContext(Dispatchers.IO)
 *    AND .first() is bounded by withTimeoutOrNull(3 s). On timeout we
 *    fall back to an empty rule map (still show installed apps).
 *  • CRITICAL FIX: search query change no longer triggers full reload
 *    (the original code already filters in-memory via combine — kept,
 *    but I'm making the contract explicit here).
 *  • Defensive: every PackageManager call uses the safe compat helper.
 *
 * v11 (2.1.1):
 *  • Use safe default flag (0) instead of MATCH_ALL.
 *  • Whole load() body wrapped in runCatching.
 */
@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getRules: GetAppRulesUseCase,
    private val upsert: UpsertAppRuleUseCase,
    private val delete: DeleteAppRuleUseCase,
    private val prefs: GuardianPreferences,
    private val rulesEngine: RulesEngine
) : ViewModel() {

    companion object {
        private const val RULES_FIRST_TIMEOUT_MS = 3_000L
    }

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)

    val apps: StateFlow<List<InstalledApp>> = combine(allApps, searchQuery) { installed, query ->
        val keyword = query.trim().lowercase()
        if (keyword.isBlank()) installed
        else installed.filter { app ->
            app.name.lowercase().contains(keyword) ||
                app.pkg.lowercase().contains(keyword)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<String> = combine(allApps, apps, isLoading) { all, filtered, loading ->
        when {
            loading && all.isEmpty() -> "Loading apps…"
            else -> "${filtered.size} / ${all.size} apps"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Loading apps…")

    init { load() }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun load() = viewModelScope.launch {
        isLoading.value = true
        // v12: ALL of this on IO, including .first() with a timeout.
        val installed = withContext(Dispatchers.IO) {
            runCatching {
                val rules: Map<String, AppRule> =
                    withTimeoutOrNull(RULES_FIRST_TIMEOUT_MS) {
                        getRules().first().associateBy { it.packageName }
                    } ?: emptyMap()

                val pm = context.packageManager
                val inputMethodPackages = AppClassifier.loadInputMethodPackages(context)
                getInstalledApplicationsCompat(pm)
                    .asSequence()
                    .filter { it.packageName != context.packageName }
                    .map { info ->
                        val label = runCatching { pm.getApplicationLabel(info).toString() }
                            .getOrDefault(info.packageName)
                            .ifBlank { info.packageName }
                        InstalledApp(
                            pkg = info.packageName,
                            name = label,
                            rule = rules[info.packageName],
                            isSystemApp = AppClassifier.isSystemApp(info),
                            isAlwaysAllowed = AppClassifier.isAlwaysAllowedPackage(
                                context.packageName,
                                info.packageName,
                                inputMethodPackages
                            )
                        )
                    }
                    .distinctBy { it.pkg }
                    .sortedWith(
                        compareByDescending<InstalledApp> { it.rule?.isBlocked == true || it.rule?.isWhitelisted == true }
                            .thenByDescending { it.isAlwaysAllowed }
                            .thenBy { it.name.lowercase() }
                    )
                    .toList()
            }.onFailure { Timber.e(it, "AppListViewModel.load failed") }
                .getOrDefault(emptyList())
        }
        allApps.value = installed
        isLoading.value = false
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch {
        if (app.isAlwaysAllowed) return@launch
        val curr = app.rule
        val nextBlocked = !(curr?.isBlocked ?: false)
        val nextWhitelisted = curr?.isWhitelisted ?: false
        val newRule = if (!nextBlocked && !nextWhitelisted) {
            runCatching { delete(app.pkg) }
            null
        } else {
            val r = AppRule(
                packageName = app.pkg,
                appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            )
            runCatching { upsert(r) }
            r
        }
        patchInMemory(app.pkg, newRule)
        runCatching { prefs.bumpRulesVersion() }
        notifyRulesChanged()
    }

    fun toggleWhitelist(app: InstalledApp) = viewModelScope.launch {
        if (app.isAlwaysAllowed) return@launch
        val curr = app.rule
        val nextWhitelisted = !(curr?.isWhitelisted ?: false)
        val nextBlocked = if (nextWhitelisted) false else (curr?.isBlocked ?: false)
        val newRule = if (!nextBlocked && !nextWhitelisted) {
            runCatching { delete(app.pkg) }
            null
        } else {
            val r = AppRule(
                packageName = app.pkg,
                appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            )
            runCatching { upsert(r) }
            r
        }
        patchInMemory(app.pkg, newRule)
        runCatching { prefs.bumpRulesVersion() }
        notifyRulesChanged()
    }

    private fun patchInMemory(pkg: String, newRule: AppRule?) {
        val current = allApps.value
        val idx = current.indexOfFirst { it.pkg == pkg }
        if (idx < 0) return
        val updated = current.toMutableList()
        updated[idx] = updated[idx].copy(rule = newRule)
        allApps.value = updated
    }

    private fun notifyRulesChanged() = viewModelScope.launch {
        runCatching { rulesEngine.reload() }
    }

    /**
     * v11 / v12 FIX: use safe default flag (0). Default flag returns the
     * full installed app list for our purposes without triggering the
     * Play-policy QUERY_ALL_PACKAGES sensitive-permission flag.
     */
    private fun getInstalledApplicationsCompat(pm: PackageManager): List<ApplicationInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        }.onFailure { Timber.w(it, "getInstalledApplications failed") }
            .getOrDefault(emptyList())
    }
}
