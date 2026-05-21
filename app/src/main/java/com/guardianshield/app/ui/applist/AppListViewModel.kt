package com.guardianshield.app.ui.applist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.data.model.AppRule
import com.guardianshield.app.data.repo.GuardianRepository
import com.guardianshield.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListViewModel(
    private val repo: GuardianRepository,
    private val pm: PackageManager
) : ViewModel() {

    private val _items = MutableStateFlow<List<AppListItem>>(emptyList())
    val items: StateFlow<List<AppListItem>> = _items

    init {
        viewModelScope.launch {
            repo.observeRules().collect { rules ->
                _items.value = buildList(rules)
            }
        }
    }

    private suspend fun buildList(rules: List<AppRule>): List<AppListItem> = withContext(Dispatchers.IO) {
        val ruleMap = rules.associateBy { it.packageName }
        val installed = try {
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (t: Throwable) { emptyList<ApplicationInfo>() }

        installed.asSequence()
            .filter { it.packageName != GuardianApp.get().packageName }
            .map { info ->
                val rule = ruleMap[info.packageName]
                AppListItem(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    isBlocked = rule?.isBlocked == true,
                    isWhitelisted = rule?.isWhitelisted == true,
                    isLocked = rule?.isLocked == true
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Returns false if the user tried to block a whitelisted app (one-way rule). */
    suspend fun setBlocked(item: AppListItem, blocked: Boolean): Boolean {
        if (blocked && item.isWhitelisted) return false
        return repo.setBlocked(item.packageName, item.label, blocked)
    }

    suspend fun setWhitelisted(item: AppListItem, whitelisted: Boolean) {
        repo.setWhitelisted(item.packageName, item.label, whitelisted)
    }

    /**
     * v2 Feature 4 — Default Allowlist.
     * Whitelists IMO, Signal, Messenger, keyboards, etc. ONLY if no rule exists
     * for that package. Does not override manual rules.
     */
    fun initDefaultAllowlist() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = GuardianApp.get()
            val prefs = ctx.getSharedPreferences("guardian_init", Context.MODE_PRIVATE)
            if (prefs.getBoolean(Constants.PREF_DEFAULT_ALLOWLIST_INIT, false)) return@launch

            for (pkg in Constants.DEFAULT_ALLOWLIST_PACKAGES) {
                val existing = repo.getRule(pkg)
                if (existing == null) {
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    repo.upsertRule(AppRule(packageName = pkg, appLabel = label, isWhitelisted = true))
                }
            }
            prefs.edit().putBoolean(Constants.PREF_DEFAULT_ALLOWLIST_INIT, true).apply()
        }
    }

    class Factory(
        private val repo: GuardianRepository,
        private val pm: PackageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppListViewModel(repo, pm) as T
    }
}
