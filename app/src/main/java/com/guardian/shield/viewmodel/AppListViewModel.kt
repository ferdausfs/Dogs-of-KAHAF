package com.guardian.shield.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.usecase.*
import com.guardian.shield.service.accessibility.GuardianAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false,
    val isCritical: Boolean = false  // Settings, DNS, Play Store etc.
)

data class AppListUiState(
    val blockedApps: List<AppRule> = emptyList(),
    val whitelistedApps: List<AppRule> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoadingInstalled: Boolean = false,
    val searchQuery: String = "",
    val showSystemApps: Boolean = true,  // FIX: Default true so user can block Settings/DNS
    val errorMessage: String? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeBlockedAppsUseCase: ObserveBlockedAppsUseCase,
    private val observeWhitelistedAppsUseCase: ObserveWhitelistedAppsUseCase,
    private val addBlockedAppUseCase: AddBlockedAppUseCase,
    private val addWhitelistedAppUseCase: AddWhitelistedAppUseCase,
    private val removeAppRuleUseCase: RemoveAppRuleUseCase
) : ViewModel() {

    companion object {
        // Critical apps that user might want to block to prevent bypass
        private val CRITICAL_PACKAGES = setOf(
            // Android Settings (to prevent disabling accessibility)
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.oneplus.security",
            // Package installers (prevent uninstall)
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            // Play Store (prevent app updates / new installs)
            "com.android.vending",
            // DNS apps (prevent DNS bypass)
            "com.frostnerd.smokescreen",
            "org.adaway",
            "com.netguard",
            "eu.faircode.netguard",
            "org.blokada.fem",
            "org.blokada.alarm.dnschanger",
            "com.kaspersky.dnschanger",
            "com.burakgon.dnschanger",
            "com.dnschanger",
            // VPN apps (often used to bypass blockers)
            "com.protonvpn.android",
            "com.expressvpn.vpn",
            "com.nordvpn.android",
            // File managers (could be used to delete app data)
            "com.android.documentsui",
            // ADB / shell apps
            "com.android.shell"
        )
    }

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    val filteredInstalledApps: StateFlow<List<InstalledApp>> = combine(
        _uiState.map { it.installedApps },
        _uiState.map { it.searchQuery },
        _uiState.map { it.showSystemApps }
    ) { apps, query, showSystem ->
        apps
            .filter { showSystem || !it.isSystem || it.isCritical }
            .filter { query.isBlank() || it.appName.contains(query, ignoreCase = true) ||
                      it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeRules()
        loadInstalledApps()
    }

    private fun observeRules() {
        viewModelScope.launch {
            observeBlockedAppsUseCase()
                .catch { e -> Timber.e(e, "observeBlocked") }
                .collect { list -> _uiState.update { it.copy(blockedApps = list) } }
        }
        viewModelScope.launch {
            observeWhitelistedAppsUseCase()
                .catch { e -> Timber.e(e, "observeWhitelisted") }
                .collect { list -> _uiState.update { it.copy(whitelistedApps = list) } }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInstalled = true) }
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    // FIX: Load ALL apps including system apps
                    pm.getInstalledApplications(0)
                        .map { info ->
                            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val isCritical = info.packageName in CRITICAL_PACKAGES
                            InstalledApp(
                                packageName = info.packageName,
                                appName = pm.getApplicationLabel(info).toString(),
                                isSystem = isSystem,
                                isCritical = isCritical
                            )
                        }
                        // Critical apps first, then user apps, then other system
                        .sortedWith(compareByDescending<InstalledApp> { it.isCritical }
                            .thenBy { it.isSystem }
                            .thenBy { it.appName.lowercase() })
                }
                _uiState.update { it.copy(installedApps = apps, isLoadingInstalled = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadInstalledApps")
                _uiState.update { it.copy(isLoadingInstalled = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleShowSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
    }

    fun addToBlockedList(app: InstalledApp) {
        viewModelScope.launch {
            addBlockedAppUseCase(
                AppRule(packageName = app.packageName, appName = app.appName, isBlocked = true)
            )
            notifyService()
        }
    }

    fun addToWhitelist(app: InstalledApp) {
        viewModelScope.launch {
            addWhitelistedAppUseCase(
                AppRule(packageName = app.packageName, appName = app.appName, isWhitelisted = true)
            )
            notifyService()
        }
    }

    fun removeRule(packageName: String) {
        viewModelScope.launch {
            removeAppRuleUseCase(packageName)
            notifyService()
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun notifyService() {
        try {
            context.sendBroadcast(
                Intent(GuardianAccessibilityService.ACTION_REFRESH_RULES).apply {
                    setPackage(context.packageName)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "notifyService failed")
        }
    }
}