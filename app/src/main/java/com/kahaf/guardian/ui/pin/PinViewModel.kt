package com.kahaf.guardian.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.usecase.SetPinUseCase
import com.kahaf.guardian.domain.usecase.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PinEvent { data object PinSetSuccess : PinEvent(); data object PinVerifySuccess : PinEvent(); data class Error(val message: String) : PinEvent() }

@HiltViewModel
class PinViewModel @Inject constructor(private val setPin: SetPinUseCase, private val verifyPin: VerifyPinUseCase) : ViewModel() {
    private val _events = MutableSharedFlow<PinEvent>(); val events: SharedFlow<PinEvent> = _events.asSharedFlow()
    private val _loading = MutableStateFlow(false); val isLoading: StateFlow<Boolean> = _loading.asStateFlow()

    fun setPin(pin: String, confirm: String) { viewModelScope.launch { _loading.value = true; try { if (pin != confirm) { _events.emit(PinEvent.Error("PINs do not match")); return@launch }; setPin(pin).fold({ _events.emit(PinEvent.PinSetSuccess) }, { _events.emit(PinEvent.Error(it.message ?: "Error")) }) } finally { _loading.value = false } } }
    fun verifyPin(pin: String) { viewModelScope.launch { _loading.value = true; try { if (verifyPin.invoke(pin)) _events.emit(PinEvent.PinVerifySuccess) else _events.emit(PinEvent.Error("Incorrect PIN")) } finally { _loading.value = false } } }
}
