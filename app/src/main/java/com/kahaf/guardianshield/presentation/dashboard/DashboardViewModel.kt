package com.kahaf.guardianshield.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val blocksToday: Int = 0,
    val recent: List<BlockEvent> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val blockEventRepository: BlockEventRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val blocksToday: StateFlow<Int> = blockEventRepository.observeBlocksTodayCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recent: StateFlow<List<BlockEvent>> = blockEventRepository.observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings { it.copy(protectionEnabled = enabled) }
        }
    }
}
