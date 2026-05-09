package com.kahaf.guardianshield.service.timed

import com.kahaf.guardianshield.domain.model.Schedule
import com.kahaf.guardianshield.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recomputes the set of currently-blocked packages whenever:
 *   - any Schedule changes (DB reactive flow)
 *   - the MinuteTickReceiver / WorkManager fires `recompute()`
 *
 * Exposes a StateFlow<Set<String>>. To respect rule R1 we expose the
 * MutableStateFlow as `Flow<Set<String>>` via `asStateFlow()` only — and
 * apply `distinctUntilChanged` ONLY on the upcasted Flow, never on the
 * StateFlow directly.
 */
@Singleton
class TimedBlockManager @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blocked.asStateFlow()

    /** Distinct-changes Flow for downstream consumers that don't want repeats. */
    val blockedPackagesFlow: Flow<Set<String>> =
        (blockedPackages as Flow<Set<String>>).distinctUntilChanged()

    @Volatile private var enabledSchedules: List<Schedule> = emptyList()

    init {
        scheduleRepository.observeAll()
            .onEach { schedules ->
                enabledSchedules = schedules.filter { it.enabled }
                recompute()
            }
            .launchIn(scope)
    }

    fun isBlockedNow(pkg: String): Boolean = pkg in _blocked.value

    fun recompute(nowMs: Long = System.currentTimeMillis()) {
        val active = HashSet<String>()
        for (s in enabledSchedules) {
            if (s.isActiveAt(nowMs)) active.addAll(s.packages)
        }
        if (active != _blocked.value) {
            _blocked.value = active
        }
    }

    fun startTicker(periodMs: Long = 60_000L) {
        scope.launch {
            while (true) {
                recompute()
                kotlinx.coroutines.delay(periodMs)
            }
        }
    }
}
