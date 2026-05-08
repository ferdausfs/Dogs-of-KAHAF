package com.guardian.shield.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getRules: GetAppRulesUseCase,
    private val upsert: UpsertAppRuleUseCase,
    private val delete: DeleteAppRuleUseCase
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
        if (!nextBlocked && !nextWhitelisted) {
            delete(app.pkg)
        } else {
            upsert(
                AppRule(
                    packageName = app.pkg,
                    appName = app.name,
                    isBlocked = nextBlocked,
                    isWhitelisted = nextWhitelisted
                )
            )
        }
        load()
        notifyRulesChanged()
    }

    fun toggleWhitelist(app: InstalledApp) = viewModelScope.launch {
        if (app.isAlwaysAllowed) return@launch
        val curr = app.rule
        val nextWhitelisted = !(curr?.isWhitelisted ?: false)
        val nextBlocked = if (nextWhitelisted) false else (curr?.isBlocked ?: false)
        if (!nextBlocked && !nextWhitelisted) {
            delete(app.pkg)
        } else {
            upsert(
                AppRule(
                    packageName = app.pkg,
                    appName = app.name,
                    isBlocked = nextBlocked,
                    isWhitelisted = nextWhitelisted
                )
            )
        }
        load()
        notifyRulesChanged()
    }

    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
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
