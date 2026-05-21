package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
) : ViewModel() {

    val keywords: StateFlow<List<KeywordRule>> = repo.observeKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(keyword: String, isRegex: Boolean) {
        if (keyword.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.upsertKeyword(KeywordRule(0, keyword.trim(), isRegex, severity = 1, enabled = true))
                prefs.bumpRulesVersion()
            } catch (_: Throwable) {}
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteKeyword(id)
                prefs.bumpRulesVersion()
            } catch (_: Throwable) {}
        }
    }
}
