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
    private val add: AddKeywordUseCase,
    private val delete: DeleteKeywordUseCase
) : ViewModel() {

    val keywords: StateFlow<List<KeywordRule>> = getKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, isRegex: Boolean = false) = viewModelScope.launch {
        if (text.isNotBlank()) {
            add(text, isRegex)
            notifyRulesChanged()  // FIX: refresh service cache
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        delete(id)
        notifyRulesChanged()      // FIX: refresh service cache
    }

    private fun notifyRulesChanged() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RulesEngine.ACTION_RULES_CHANGED))
    }
}
