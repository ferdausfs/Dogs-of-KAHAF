package com.guardianshield.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guardianshield.app.data.repo.GuardianRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(private val repo: GuardianRepository) : ViewModel() {

    data class Stats(
        val blockedAppsCount: Int = 0,
        val scrollRemindersToday: Int = 0,
        val aiBlocksToday: Int = 0,
        val totalBlockedToday: Int = 0
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    init {
        viewModelScope.launch {
            combine(repo.observeRules(), repo.observeRecentLogs()) { rules, logs ->
                val startOfDay = startOfTodayMs()
                val today = logs.filter { it.timestamp >= startOfDay }
                Stats(
                    blockedAppsCount = rules.count { it.isBlocked },
                    scrollRemindersToday = today.count { it.eventType == "SCROLL_REMINDER" },
                    aiBlocksToday = today.count { it.eventType == "AI_BLOCK_24H" },
                    totalBlockedToday = today.count { it.eventType == "BLOCK" || it.eventType == "AI_BLOCK_24H" }
                )
            }.collect { _stats.value = it }
        }
    }

    private fun startOfTodayMs(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    class Factory(private val repo: GuardianRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(repo) as T
    }
}
