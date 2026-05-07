package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import com.guardian.shield.service.detection.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PinViewModel @Inject constructor(private val pin: PinManager) : ViewModel() {
    fun isPinSet() = pin.isPinSet()
    fun setPin(value: String) = pin.setPin(value)
    fun verify(value: String) = pin.verifyPin(value)
}
