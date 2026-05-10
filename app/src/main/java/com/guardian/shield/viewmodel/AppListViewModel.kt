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

/**
 * FIX-LOG (vs original):
 *  - BUG #11: toggleBlock used to delete the row when the app was already
 *    blocked, which also wiped the whitelist flag. We now keep the row and
 *    just flip isBlocked, so block/whitelist flags are independent.
 *  - Skip our own package from the displayed list (was confusingly listed).
 */
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
                .filter { it.packageName != context.packageName }   // hide self
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), rules[it.packageName]) }
                .sortedBy { it.name.lowercase() }
        }
        _apps.value = installed
    }

    fun toggleBlock(app: InstalledApp) = viewModelScope.launch {
        val curr = app.rule
        val nextBlocked = !(curr?.isBlocked ?: false)
        val nextWhitelisted = curr?.isWhitelisted ?: false
        if (!nextBlocked && !nextWhitelisted) {
            // Both flags off → no need to keep an empty row.
            delete(app.pkg)
        } else {
            upsert(AppRule(
                packageName = app.pkg, appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            ))
        }
        load()
        notifyRulesChanged()
    }

    fun toggleWhitelist(app: InstalledApp) = viewModelScope.launch {
        val curr = app.rule
        val nextWhitelisted = !(curr?.isWhitelisted ?: false)
        // Whitelist trumps block — clear block flag when whitelisting.
        val nextBlocked = if (nextWhitelisted) false else (curr?.isBlocked ?: false)
        if (!nextBlocked && !nextWhitelisted) {
            delete(app.pkg)
        } else {
            upsert(AppRule(
                packageName = app.pkg, appName = app.name,
                isBlocked = nextBlocked,
                isWhitelisted = nextWhitelisted
            ))
        }
        load()
        notifyRulesChanged()
    }

    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
    }
}
