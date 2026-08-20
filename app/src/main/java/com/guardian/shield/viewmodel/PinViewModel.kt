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

    // PHASE 1c (v3.5.0) — recovery pass-throughs (PinManager is the single
    // source of truth; see its KDoc for the deliberately non-trivial design).
    fun generateRecoveryCode(): String? = pinManager.generateRecoveryCode()
    fun hasRecoveryCode(): Boolean = pinManager.hasRecoveryCode()
}
