package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.presentation.dashboard.DashboardViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    private fun stubEventRepo(): BlockEventRepository {
        val r = mockk<BlockEventRepository>(relaxed = true)
        every { r.observeBlocksTodayCount() } returns MutableStateFlow(0)
        every { r.observeRecent(any()) } returns MutableStateFlow(emptyList())
        every { r.getBlocksByReason(any()) } returns
                MutableStateFlow(emptyMap<BlockReason, Int>())
        every { r.getTopBlockedApps(any(), any()) } returns
                MutableStateFlow(emptyList<Pair<String, Int>>())
        return r
    }

    @Test
    fun `setProtection updates app settings via repository`() = runTest {
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val eventRepo = stubEventRepo()
        val initial = AppSettings(protectionEnabled = true)
        val flow = MutableStateFlow(initial)
        every { settingsRepo.appSettings } returns flow

        val captured = slot<(AppSettings) -> AppSettings>()
        coEvery { settingsRepo.updateAppSettings(capture(captured)) } answers {}

        val vm = DashboardViewModel(settingsRepo, eventRepo)
        vm.setProtection(false)
        advanceUntilIdle()

        coVerify { settingsRepo.updateAppSettings(any()) }
        val transformed = captured.captured(AppSettings(protectionEnabled = true))
        assertFalse(transformed.protectionEnabled)
    }

    @Test
    fun `setProtection true keeps it enabled`() = runTest {
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val eventRepo = stubEventRepo()
        every { settingsRepo.appSettings } returns MutableStateFlow(AppSettings())

        val captured = slot<(AppSettings) -> AppSettings>()
        coEvery { settingsRepo.updateAppSettings(capture(captured)) } answers {}

        val vm = DashboardViewModel(settingsRepo, eventRepo)
        vm.setProtection(true)
        advanceUntilIdle()

        val transformed = captured.captured(AppSettings(protectionEnabled = false))
        assertTrue(transformed.protectionEnabled)
    }
}
