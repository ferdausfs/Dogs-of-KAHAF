package com.kahaf.guardianshield.presentation.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.domain.model.InstalledApp
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRow(
    val app: InstalledApp,
    val state: AppRuleState
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppListViewModel @Inject constructor(
    private val appRuleRepository: AppRuleRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val rows: StateFlow<List<AppRow>> =
        combine(_allApps, appRuleRepository.observeAll(), _query) { apps, rules, q ->
            val rulesMap = rules.associate { it.packageName to it.state }
            apps.asSequence()
                .filter { q.isBlank() ||
                        it.label.contains(q, ignoreCase = true) ||
                        it.packageName.contains(q, ignoreCase = true) }
                .map { AppRow(it, rulesMap[it.packageName] ?: AppRuleState.NORMAL) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = appRuleRepository.getInstalledApps(includeSystem = false)
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun setState(pkg: String, state: AppRuleState) {
        viewModelScope.launch { appRuleRepository.setState(pkg, state) }
    }
}
