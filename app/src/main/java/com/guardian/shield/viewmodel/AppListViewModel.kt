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
import javax.inject.Inject

data class InstalledApp(
    val pkg: String,
    val name: String,
    val rule: AppRule?,
    val isSystemApp: Boolean,
    val isAlwaysAllowed: Boolean
)

/**
 * v9 (2.0.0):
 *  • P2-B → LocalBroadcastManager removed. Rule changes are propagated by
 *    calling rulesEngine.reload() directly; observers (e.g. the
 *    AccessibilityService) get notified via RulesEngine.rulesChanged.
 *
 * Earlier v8 BUG-11 / BUG-12 still apply.
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

    /**
     * P2-B: directly reload RulesEngine. Subscribers (the AccessibilityService)
     * are notified via the rulesChanged SharedFlow that reload() emits to.
     */
    private fun notifyRulesChanged() = viewModelScope.launch {
        runCatching { rulesEngine.reload() }
    }

    private fun getInstalledApplicationsCompat(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_ALL)
        }
    }
}
