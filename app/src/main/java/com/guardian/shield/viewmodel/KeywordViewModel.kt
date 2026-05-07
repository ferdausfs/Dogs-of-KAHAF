package com.guardian.shield.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

@HiltViewModel
class KeywordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,   // FIX: need context for broadcast
    getKeywords: GetKeywordsUseCase,
    // FIX: renamed to avoid shadowing fun add() and fun delete() below
    //      (was causing "recursive problem" Kotlin type error → build failure)
    private val addUseCase: AddKeywordUseCase,
    private val deleteUseCase: DeleteKeywordUseCase
) : ViewModel() {

    val keywords: StateFlow<List<KeywordRule>> = getKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, isRegex: Boolean = false) = viewModelScope.launch {
        if (text.isNotBlank()) {
            addUseCase(text, isRegex)
            notifyRulesChanged()
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        deleteUseCase(id)
        notifyRulesChanged()
    }

    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
    }
}
