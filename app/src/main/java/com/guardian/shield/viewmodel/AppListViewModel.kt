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
 * v11 (2.1.1) STABILITY PATCH:
 *  • CRITICAL FIX: getInstalledApplications() now uses a safe default
 *    flag (0) instead of MATCH_ALL. MATCH_ALL requires
 *    QUERY_ALL_PACKAGES privilege, and on Android 11+ Play-flagged
 *    devices it can throw SecurityException — previously crashing the
 *    AppList screen.
 *  • DEFENSIVE: load() entire body wrapped in try/catch — even the
 *    package manager throws on some OEMs.
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

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")

    val apps: StateFlow<List<InstalledApp>> = combine(allApps, searchQuery) { installed, query ->
        val keyword = query.trim().lowercase()
        installed.filter { app ->
            keyword.isBlank() ||
                app.name.lowercase().contains(keyword) ||
                app.pkg.lowercase().contains(keyword)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<String> = combine(allApps, apps) { all, filtered ->
        "${filtered.size} / ${all.size} apps"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0 / 0 apps")

    init { load() }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun load() = viewModelScope.launch {
        runCatching {
            val rules = getRules().first().associateBy { it.packageName }
            val installed = withContext(Dispatchers.IO) {
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
            }
            allApps.value = installed
        }.onFailure { Timber.e(it, "AppListViewModel.load failed") }
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch {
        if (app.isAlwaysAllowed) return@launch
        val curr = app.rule
        val nextBlocked = !(curr?.isBlocked ?: false)
        val nextWhitelisted = curr?.isWhitelisted ?: false
        val newRule = if (!nextBlocked && !nextWhitelisted) {
            delete(app.pkg)
            null
        } else {
            val r = AppRule(
                packageName = app.pkg,
                appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            )
            upsert(r)
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
            delete(app.pkg)
            null
        } else {
            val r = AppRule(
                packageName = app.pkg,
                appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            )
            upsert(r)
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
     * v11 FIX: use safe default flag (0) — MATCH_ALL requires
     * QUERY_ALL_PACKAGES which Play Store flags as "sensitive".
     * The default flag returns the same set for our purposes.
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
