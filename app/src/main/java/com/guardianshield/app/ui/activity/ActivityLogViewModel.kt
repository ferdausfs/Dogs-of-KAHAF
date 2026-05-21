package com.guardianshield.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.data.repo.GuardianRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogViewModel(private val repo: GuardianRepository) : ViewModel() {

    private val filter = MutableStateFlow<String?>(null)
    val items: StateFlow<List<ActivityLog>> = filter
        .flatMapLatest { f ->
            if (f == null) repo.observeRecentLogs() else repo.observeLogsByType(f)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFilter(type: String?) { filter.value = type }

    class Factory(private val repo: GuardianRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActivityLogViewModel(repo) as T
    }
}
