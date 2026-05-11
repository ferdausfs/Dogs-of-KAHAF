package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class BlockStats(
    val totalBlocks: Int = 0,
    val aiBlocks: Int = 0,
    val keywordBlocks: Int = 0,
    val topApp: String? = null
)

data class DashboardUiState(
    val recent: List<BlockEvent> = emptyList(),
    val todayCount: Int = 0,
    val protectionActive: Boolean = false,
    val protectionEnabled: Boolean = true,
    val stats: BlockStats = BlockStats()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
) : ViewModel() {

    private val _protectionActive = MutableStateFlow(false)

    private val todayStart: Long get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observeEvents(20),
        repo.countSinceFlow(todayStart),
        repo.countByReasonFlow(todayStart, BlockReason.AI_DETECTION),
        repo.countByReasonFlow(todayStart, BlockReason.KEYWORD_MATCH),
        repo.topPackageFlow(todayStart),
        prefs.protectionEnabled,
        _protectionActive
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val recent = values[0] as List<BlockEvent>
        val count = values[1] as Int
        val ai = values[2] as Int
        val kw = values[3] as Int
        val top = values[4] as String?
        val enabled = values[5] as Boolean
        val active = values[6] as Boolean
        DashboardUiState(
            recent = recent,
            todayCount = count,
            protectionActive = active,
            protectionEnabled = enabled,
            stats = BlockStats(count, ai, kw, top)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setProtectionActive(active: Boolean) { _protectionActive.value = active }

    fun toggleProtection() {
        viewModelScope.launch {
            val current = prefs.protectionEnabled.first()
            prefs.setProtectionEnabled(!current)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.clearEvents() } catch (_: Throwable) {}
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.deleteEvent(id) } catch (_: Throwable) {}
        }
    }

    suspend fun getAllEvents(): List<BlockEvent> = withContext(Dispatchers.IO) {
        try { repo.allEvents() } catch (_: Throwable) { emptyList() }
    }
}
