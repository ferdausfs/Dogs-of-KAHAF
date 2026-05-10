package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.usecase.AddKeywordUseCase
import com.guardian.shield.domain.usecase.DeleteKeywordUseCase
import com.guardian.shield.domain.usecase.GetKeywordsUseCase
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v9 (2.0.0):
 *  • P2-B → LocalBroadcastManager removed; we call rulesEngine.reload() and
 *    let observers pick the change up via the rulesChanged SharedFlow.
 *
 * Earlier v8 BUG-12 still applies: bumps prefs.rulesVersion on every mutation.
 */
@HiltViewModel
class KeywordViewModel @Inject constructor(
    getKeywords: GetKeywordsUseCase,
    private val addUseCase: AddKeywordUseCase,
    private val deleteUseCase: DeleteKeywordUseCase,
    private val prefs: GuardianPreferences,
    private val rulesEngine: RulesEngine
) : ViewModel() {

    val keywords: StateFlow<List<KeywordRule>> = getKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, isRegex: Boolean = false) = viewModelScope.launch {
        if (text.isNotBlank()) {
            addUseCase(text, isRegex)
            runCatching { prefs.bumpRulesVersion() }
            notifyRulesChanged()
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        deleteUseCase(id)
        runCatching { prefs.bumpRulesVersion() }
        notifyRulesChanged()
    }

    private fun notifyRulesChanged() = viewModelScope.launch {
        runCatching { rulesEngine.reload() }
    }
}
