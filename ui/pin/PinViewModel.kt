package com.kahaf.guardian.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.usecase.SetPinUseCase
import com.kahaf.guardian.domain.usecase.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PinEvent {
    data object PinSetSuccess : PinEvent()
    data object PinVerifySuccess : PinEvent()
    data class Error(val message: String) : PinEvent()
}

@HiltViewModel
class PinViewModel @Inject constructor(
    private val setPinUseCase: SetPinUseCase,
    private val verifyPinUseCase: VerifyPinUseCase
) : ViewModel() {

    private val _events = MutableSharedFlow<PinEvent>()
    val events: SharedFlow<PinEvent> = _events.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setPin(pin: String, confirmPin: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (pin != confirmPin) {
                    _events.emit(PinEvent.Error("PINs do not match"))
                    return@launch
                }
                val result = setPinUseCase(pin)
                result.fold(
                    onSuccess = { _events.emit(PinEvent.PinSetSuccess) },
                    onFailure = { _events.emit(PinEvent.Error(it.message ?: "Failed to set PIN")) }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val isValid = verifyPinUseCase(pin)
                if (isValid) {
                    _events.emit(PinEvent.PinVerifySuccess)
                } else {
                    _events.emit(PinEvent.Error("Incorrect PIN"))
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}