package com.guardian.shield.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.usecase.AddKeywordUseCase
import com.guardian.shield.domain.usecase.DeleteKeywordUseCase
import com.guardian.shield.domain.usecase.GetKeywordsUseCase
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-12 → after every keyword add/delete we bump prefs.rulesVersion so
 *    MainActivity.onResume can skip RulesEngine.reload() when nothing changed.
 */
@HiltViewModel
class KeywordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    getKeywords: GetKeywordsUseCase,
    private val addUseCase: AddKeywordUseCase,
    private val deleteUseCase: DeleteKeywordUseCase,
    private val prefs: GuardianPreferences
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

    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
    }
}
