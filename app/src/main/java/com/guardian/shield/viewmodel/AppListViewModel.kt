package com.guardian.shield.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.usecase.*
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(val pkg: String, val name: String, val rule: AppRule?)

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getRules: GetAppRulesUseCase,
    private val upsert: UpsertAppRuleUseCase,
    private val delete: DeleteAppRuleUseCase
) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val rules = getRules().first().associateBy { it.packageName }
        val installed = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), rules[it.packageName]) }
                .sortedBy { it.name.lowercase() }
        }
        _apps.value = installed
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch {
        val curr = app.rule
        if (curr == null || (!curr.isBlocked && !curr.isWhitelisted)) {
            upsert(AppRule(packageName = app.pkg, appName = app.name, isBlocked = true))
        } else if (curr.isBlocked) {
            delete(app.pkg)
        }
        load()
        notifyRulesChanged()  // FIX: tell the accessibility service to refresh its cache
    }

    fun toggleWhitelist(app: InstalledApp) = viewModelScope.launch {
        val curr = app.rule
        upsert(AppRule(
            packageName = app.pkg, appName = app.name,
            isBlocked = false,                              // always clear block when whitelisting
            isWhitelisted = !(curr?.isWhitelisted ?: false)
        ))
        load()
        notifyRulesChanged()  // FIX: tell the accessibility service to refresh its cache
    }

    /** Sends a LocalBroadcast so GuardianAccessibilityService reloads its in-memory rule cache. */
    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
    }
}
