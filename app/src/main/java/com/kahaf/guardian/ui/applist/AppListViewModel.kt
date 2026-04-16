package com.kahaf.guardian.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.domain.usecase.*
import com.kahaf.guardian.util.PackageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppListUiState(val isLoading: Boolean = true, val apps: List<AppInfo> = emptyList(), val filteredApps: List<AppInfo> = emptyList(), val searchQuery: String = "", val currentTab: Int = 0)

@HiltViewModel
class AppListViewModel @Inject constructor(
    app: Application, private val getBlocked: GetBlockedAppsUseCase,
    private val getWhitelisted: GetWhitelistedAppsUseCase,
    private val toggleBlock: ToggleAppBlockedUseCase,
    private val toggleWhitelist: ToggleAppWhitelistedUseCase
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AppListUiState()); val uiState: StateFlow<AppListUiState> = _state.asStateFlow()
    private var allApps: List<AppInfo> = emptyList()

    init { viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val installed = withContext(Dispatchers.IO) { PackageUtils.getInstalledApps(getApplication()) }
        combine(getBlocked(), getWhitelisted()) { b, w ->
            val bs = b.map { it.packageName }.toSet(); val ws = w.map { it.packageName }.toSet()
            installed.map { it.copy(isBlocked = it.packageName in bs, isWhitelisted = it.packageName in ws) }
        }.collect { merged -> allApps = merged; _state.update { it.copy(isLoading = false, apps = merged, filteredApps = filter(merged, it.searchQuery, it.currentTab)) } }
    } }

    fun setSearchQuery(q: String) { _state.update { it.copy(searchQuery = q, filteredApps = filter(allApps, q, it.currentTab)) } }
    fun setTab(t: Int) { _state.update { it.copy(currentTab = t, filteredApps = filter(allApps, it.searchQuery, t)) } }
    fun toggleBlocked(app: AppInfo) { viewModelScope.launch { toggleBlock(app.packageName, app.appName, !app.isBlocked) } }
    fun toggleWhitelisted(app: AppInfo) { viewModelScope.launch { toggleWhitelist(app.packageName, app.appName, !app.isWhitelisted) } }

    private fun filter(apps: List<AppInfo>, q: String, tab: Int): List<AppInfo> {
        val t = when (tab) { 1 -> apps.filter { it.isBlocked }; 2 -> apps.filter { it.isWhitelisted }; else -> apps }
        return if (q.isBlank()) t else t.filter { it.appName.contains(q, true) || it.packageName.contains(q, true) }
    }
}
