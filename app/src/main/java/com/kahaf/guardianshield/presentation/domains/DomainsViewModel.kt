package com.kahaf.guardianshield.presentation.domains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.data.repository.DomainRepositoryImpl
import com.kahaf.guardianshield.domain.model.DomainRule
import com.kahaf.guardianshield.domain.repository.DomainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DomainsViewModel @Inject constructor(
    private val domainRepository: DomainRepository
) : ViewModel() {

    val rules: StateFlow<List<DomainRule>> = domainRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun add(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            try {
                val normalized = DomainRepositoryImpl.normalize(input)
                if (!DomainRepositoryImpl.isValid(normalized)) {
                    _error.value = "invalid"
                    return@launch
                }
                domainRepository.add(normalized)
                _error.value = null
            } catch (t: Throwable) {
                _error.value = t.message ?: "invalid"
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { domainRepository.delete(id) }
    }

    fun clearError() { _error.value = null }
}
