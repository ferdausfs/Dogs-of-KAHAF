package com.kahaf.guardianshield.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.data.PinManager
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val blocksToday: Int = 0,
    val recent: List<BlockEvent> = emptyList()
)

/**
 * v3.0.0:
 *  - exposes `appSettings` (replaces older `settings` to better convey scope).
 *  - exposes `verifyPin()` for PIN-gated navigation to Settings.
 *  - exposes `blocksByReasonToday` and `topBlockedAppsToday` for the new
 *    "Today's Activity" card.
 */
@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val blockEventRepository: BlockEventRepository
) : ViewModel() {

    /**
     * Re-emits `startOfTodayMs` every minute so day-rollover is handled.
     *
     * v3.1.2: `distinctUntilChanged` so the downstream `flatMapLatest`
     * subscriptions don't tear down and re-subscribe the DAO Flow every
     * minute — they only re-subscribe when the day actually rolls over.
     */
    private val todayTicker = flow {
        while (true) {
            emit(startOfTodayMs())
            kotlinx.coroutines.delay(TimeUnit.MINUTES.toMillis(1))
        }
    }.distinctUntilChanged()

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Backwards-compatible alias used by existing UI/tests. */
    val settings: StateFlow<AppSettings> = appSettings

    val blocksToday: StateFlow<Int> = blockEventRepository.observeBlocksTodayCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recent: StateFlow<List<BlockEvent>> = blockEventRepository.observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val blocksByReasonToday: StateFlow<Map<BlockReason, Int>> =
        todayTicker.flatMapLatest { since ->
            blockEventRepository.getBlocksByReason(since)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val topBlockedAppsToday: StateFlow<List<Pair<String, Int>>> =
        todayTicker.flatMapLatest { since ->
            blockEventRepository.getTopBlockedApps(since, 3)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings { it.copy(protectionEnabled = enabled) }
        }
    }

    /**
     * Verify the current Settings PIN. Returns `true` if the input matches the
     * stored hash (or if no PIN is set).
     */
    suspend fun verifyPin(pin: String): Boolean {
        val app = settingsRepository.appSettings.first()
        if (!app.settingsPinEnabled || app.settingsPinHash.isBlank()) return true
        return PinManager.verify(pin, app.settingsPinHash)
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
