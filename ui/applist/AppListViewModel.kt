package com.kahaf.guardian.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.domain.usecase.GetBlockedAppsUseCase
import com.kahaf.guardian.domain.usecase.GetWhitelistedAppsUseCase
import com.kahaf.guardian.domain.usecase.ToggleAppBlockedUseCase
import com.kahaf.guardian.domain.usecase.ToggleAppWhitelistedUseCase
import com.kahaf.guardian.util.PackageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppListUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val currentTab: Int = 0 // 0=All, 1=Blocked, 2=Whitelisted
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    application: Application,
    private val getBlockedAppsUseCase: GetBlockedAppsUseCase,
    private val getWhitelistedAppsUseCase: GetWhitelistedAppsUseCase,
    private val toggleAppBlockedUseCase: ToggleAppBlockedUseCase,
    private val toggleAppWhitelistedUseCase: ToggleAppWhitelistedUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private var allInstalledApps: List<AppInfo> = emptyList()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load installed apps
            val installed = withContext(Dispatchers.IO) {
                PackageUtils.getInstalledApps(getApplication())
            }

            // Observe blocked and whitelisted to merge with installed
            combine(
                getBlockedAppsUseCase(),
                getWhitelistedAppsUseCase()
            ) { blocked, whitelisted ->
                val blockedSet = blocked.map { it.packageName }.toSet()
                val whitelistedSet = whitelisted.map { it.packageName }.toSet()

                installed.map { app ->
                    app.copy(
                        isBlocked = app.packageName in blockedSet,
                        isWhitelisted = app.packageName in whitelistedSet
                    )
                }
            }.collect { mergedApps ->
                allInstalledApps = mergedApps
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = mergedApps,
                        filteredApps = filterApps(mergedApps, it.searchQuery, it.currentTab)
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredApps = filterApps(allInstalledApps, query, state.currentTab)
            )
        }
    }

    fun setTab(tab: Int) {
        _uiState.update { state ->
            state.copy(
                currentTab = tab,
                filteredApps = filterApps(allInstalledApps, state.searchQuery, tab)
            )
        }
    }

    fun toggleBlocked(app: AppInfo) {
        viewModelScope.launch {
            toggleAppBlockedUseCase(app.packageName, app.appName, !app.isBlocked)
        }
    }

    fun toggleWhitelisted(app: AppInfo) {
        viewModelScope.launch {
            toggleAppWhitelistedUseCase(app.packageName, app.appName, !app.isWhitelisted)
        }
    }

    private fun filterApps(apps: List<AppInfo>, query: String, tab: Int): List<AppInfo> {
        val tabFiltered = when (tab) {
            1 -> apps.filter { it.isBlocked }
            2 -> apps.filter { it.isWhitelisted }
            else -> apps
        }

        return if (query.isBlank()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
}