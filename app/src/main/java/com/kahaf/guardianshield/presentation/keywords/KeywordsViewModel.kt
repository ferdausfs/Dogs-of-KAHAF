package com.kahaf.guardianshield.presentation.keywords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.KeywordRule
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordsViewModel @Inject constructor(
    private val keywordRepository: KeywordRepository
) : ViewModel() {

    val rules: StateFlow<List<KeywordRule>> = keywordRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun add(pattern: String, isRegex: Boolean) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            try {
                keywordRepository.add(pattern.trim(), isRegex)
                _error.value = null
            } catch (t: Throwable) {
                _error.value = t.message
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { keywordRepository.delete(id) }
    }

    fun clearError() { _error.value = null }
}
