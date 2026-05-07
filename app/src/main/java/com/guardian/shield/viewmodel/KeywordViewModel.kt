package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordViewModel @Inject constructor(
    getKeywords: GetKeywordsUseCase,
    private val add: AddKeywordUseCase,
    private val delete: DeleteKeywordUseCase
) : ViewModel() {
    val keywords: StateFlow<List<KeywordRule>> = getKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, isRegex: Boolean = false) = viewModelScope.launch {
        if (text.isNotBlank()) add(text, isRegex)
    }
    fun delete(id: Long) = viewModelScope.launch { delete(id) }
}
