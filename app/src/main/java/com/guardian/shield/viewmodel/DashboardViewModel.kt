package com.guardian.shield.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockStats
import com.guardian.shield.domain.model.ProtectionState
import com.guardian.shield.domain.usecase.*
import com.guardian.shield.service.accessibility.GuardianAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class DashboardUiState(
    val protectionState: ProtectionState = ProtectionState(),
    val stats: BlockStats                = BlockStats(),
    val recentEvents: List<BlockEvent>   = emptyList(),
    // FIX: isProtectionOn kept for switch state but derived from protectionState
    val isProtectionOn: Boolean          = true,
    val isLoading: Boolean               = false,
    val errorMessage: String?            = null
)

private data class PrefSnapshot(
    val protection: Boolean,
    val ai: Boolean,
    val keyword: Boolean,
    val delay: Int
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeBlockEventsUseCase: ObserveBlockEventsUseCase,
    private val getBlockStatsUseCase: GetBlockStatsUseCase,
    private val toggleProtectionUseCase: ToggleProtectionUseCase,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val prefs: GuardianPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
        observeRecentEvents()
        observePrefs()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // FIX: loadInitial does NOT load stats — observeRecentEvents handles that
                // Prevents double DB query on init
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadInitial error")
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun observeRecentEvents() {
        viewModelScope.launch {
            observeBlockEventsUseCase()
                .catch { e ->
                    Timber.e(e, "observeRecentEvents error")
                    _uiState.update { it.copy(errorMessage = "Failed to load events") }
                }
                .collect { events ->
                    _uiState.update { it.copy(recentEvents = events.take(10)) }
                    try {
                        val stats = getBlockStatsUseCase()
                        _uiState.update { it.copy(stats = stats) }
                    } catch (e: Exception) {
                        Timber.e(e, "stats refresh error")
                    }
                }
        }
    }

    private fun observePrefs() {
        viewModelScope.launch {
            // FIX: transform block returns data — no side effects inside combine
            combine(
                prefs.isProtectionEnabled,
                prefs.isAiDetectionEnabled,
                prefs.isKeywordDetectionEnabled,
                prefs.delayUnlockSeconds
            ) { protection: Boolean, ai: Boolean, keyword: Boolean, delay: Int ->
                PrefSnapshot(protection, ai, keyword, delay)
            }.collect { snap ->
                _uiState.update {
                    it.copy(isProtectionOn = snap.protection)
                }
            }
        }
    }

    fun refreshProtectionState() {
        // FIX: IO dispatcher — isPinSetUseCase reads EncryptedSharedPreferences (blocking I/O)
        viewModelScope.launch(Dispatchers.IO) {
            val isPinSet      = isPinSetUseCase()
            val accessibility = isAccessibilityEnabled()
            val protState     = ProtectionState(
                isAccessibilityEnabled     = accessibility,
                isOverlayPermissionGranted = true,
                isPinSet                   = isPinSet,
                isProtectionActive         = accessibility && _uiState.value.isProtectionOn
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(protectionState = protState) }
            }
        }
    }

    fun toggleProtection(enabled: Boolean) {
        viewModelScope.launch {
            toggleProtectionUseCase(enabled)
            notifyServiceRulesChanged()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun notifyServiceRulesChanged() {
        try {
            context.sendBroadcast(
                Intent(GuardianAccessibilityService.ACTION_REFRESH_RULES).apply {
                    setPackage(context.packageName)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "notifyServiceRulesChanged failed")
        }
    }

    // FIX: Full component name check — not fragile contains()
    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager
            val expectedId = "${context.packageName}/" +
                "com.guardian.shield.service.accessibility.GuardianAccessibilityService"
            am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { it.id == expectedId }
        } catch (_: Exception) { false }
    }
}