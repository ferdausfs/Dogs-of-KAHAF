package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.presentation.aisettings.AiSettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    @Test
    fun `toggleSource adds package to set`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val appRepo = mockk<AppRuleRepository>(relaxed = true)
        every { repo.aiSettings } returns MutableStateFlow(AiSettings())
        coEvery { appRepo.getInstalledApps(any()) } returns emptyList()

        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo)
        vm.toggleSource("com.example.app", true)
        advanceUntilIdle()

        val out = captured.captured(AiSettings(contentSourcePackages = emptySet()))
        assertTrue("com.example.app" in out.contentSourcePackages)
    }

    @Test
    fun `setSensitivity persists value`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val appRepo = mockk<AppRuleRepository>(relaxed = true)
        every { repo.aiSettings } returns MutableStateFlow(AiSettings())
        coEvery { appRepo.getInstalledApps(any()) } returns emptyList()

        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo)
        vm.setSensitivity(0.8f)
        advanceUntilIdle()

        val out = captured.captured(AiSettings(sensitivity = 0.1f))
        assertEquals(0.8f, out.sensitivity, 0.0001f)
    }
}
