package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.presentation.dashboard.DashboardViewModel
import io.mockk.coVerify
import io.mockk.coEvery
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

    @Test
    fun `setProtection updates app settings via repository`() = runTest {
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val eventRepo = mockk<BlockEventRepository>(relaxed = true)
        val initial = AppSettings(protectionEnabled = true)
        val flow = MutableStateFlow(initial)
        every { settingsRepo.appSettings } returns flow
        every { eventRepo.observeBlocksTodayCount() } returns MutableStateFlow(0)
        every { eventRepo.observeRecent(any()) } returns MutableStateFlow(emptyList())

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
        val eventRepo = mockk<BlockEventRepository>(relaxed = true)
        every { settingsRepo.appSettings } returns MutableStateFlow(AppSettings())
        every { eventRepo.observeBlocksTodayCount() } returns MutableStateFlow(0)
        every { eventRepo.observeRecent(any()) } returns MutableStateFlow(emptyList())

        val captured = slot<(AppSettings) -> AppSettings>()
        coEvery { settingsRepo.updateAppSettings(capture(captured)) } answers {}

        val vm = DashboardViewModel(settingsRepo, eventRepo)
        vm.setProtection(true)
        advanceUntilIdle()

        val transformed = captured.captured(AppSettings(protectionEnabled = false))
        assertTrue(transformed.protectionEnabled)
    }
}
