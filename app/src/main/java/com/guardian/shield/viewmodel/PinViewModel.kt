package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import com.guardian.shield.service.detection.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PinViewModel @Inject constructor(
    private val pinManager: PinManager
) : ViewModel() {
    fun isPinSet(): Boolean = pinManager.isPinSet()
    fun setPin(pin: String): Boolean = pinManager.setPin(pin)
    fun verifyPin(pin: String): PinManager.VerifyResult = pinManager.verifyPin(pin)
    fun clearPin() = pinManager.clearPin()
}
