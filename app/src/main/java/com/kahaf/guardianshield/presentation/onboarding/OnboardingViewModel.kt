package com.kahaf.guardianshield.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.kahaf.guardianshield.data.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class OnboardingUiState(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val battery: Boolean = false
) {
    val canFinish: Boolean get() = accessibility && overlay
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val s = permissionManager.refresh()
        _state.value = OnboardingUiState(
            accessibility = s.accessibility,
            overlay = s.overlay,
            notifications = s.notifications,
            battery = s.ignoreBatteryOpt
        )
    }

    fun openAccessibility() = permissionManager.openAccessibilitySettings()
    fun openOverlay() = permissionManager.openOverlaySettings()
    fun openNotifications() = permissionManager.openNotificationSettings()
    fun openBattery() = permissionManager.openBatteryOptimizationSettings()
}
