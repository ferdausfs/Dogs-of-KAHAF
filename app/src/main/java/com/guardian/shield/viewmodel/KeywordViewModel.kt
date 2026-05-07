package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.usecase.AddKeywordUseCase
import com.guardian.shield.domain.usecase.DeleteKeywordUseCase
import com.guardian.shield.domain.usecase.GetKeywordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordViewModel @Inject constructor(
    getKeywords: GetKeywordsUseCase,
    private val addKeyword: AddKeywordUseCase,      // ✅ নাম পাল্টে দিলাম
    private val deleteKeyword: DeleteKeywordUseCase // ✅ নাম পাল্টে দিলাম
) : ViewModel() {

    val keywords: StateFlow<List<KeywordRule>> = getKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, isRegex: Boolean = false) {
        if (text.isBlank()) return
        viewModelScope.launch {
            addKeyword(text, isRegex)  // ✅ এখন আলাদা নাম, recursion নেই
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteKeyword(id)  // ✅ এখন আলাদা নাম
        }
    }
}