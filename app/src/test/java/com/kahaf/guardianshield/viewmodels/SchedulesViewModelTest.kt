package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.DaysMask
import com.kahaf.guardianshield.domain.model.Schedule
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.ScheduleRepository
import com.kahaf.guardianshield.presentation.schedules.SchedulesViewModel
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    @Test
    fun `upsert delegates to repository and triggers recompute`() = runTest {
        val schedRepo = mockk<ScheduleRepository>(relaxed = true)
        val appRepo = mockk<AppRuleRepository>(relaxed = true)
        val timed = mockk<TimedBlockManager>(relaxed = true)
        every { schedRepo.observeAll() } returns MutableStateFlow(emptyList())
        coEvery { appRepo.getInstalledApps(any()) } returns emptyList()
        coEvery { schedRepo.upsert(any()) } returns 1L

        val vm = SchedulesViewModel(schedRepo, appRepo, timed)
        val s = Schedule(0L, "Night", DaysMask.ALL, 22 * 60, 6 * 60, listOf("a"), true)
        vm.upsert(s)
        advanceUntilIdle()

        coVerify { schedRepo.upsert(s) }
        // v3.1.2 FIX: TimedBlockManager.recompute() has a default Long parameter,
        // so the Kotlin compiler emits a call into the synthetic `recompute$default`
        // bridge. MockK's `verify` only sees the underlying `recompute(Long)` call,
        // so we must match the actual signature with `any()`.
        verify { timed.recompute(any()) }
    }

    @Test
    fun `emptySchedule returns enabled night schedule`() {
        val vm = SchedulesViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val s = vm.emptySchedule()
        assertTrue(s.enabled)
        assertTrue(s.daysMask == DaysMask.ALL)
    }
}
