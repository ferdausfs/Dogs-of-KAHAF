package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.ThemeMode
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.domain.usecase.ExportImportConfigUseCase
import com.kahaf.guardianshield.presentation.settings.SettingsViewModel
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    @Test
    fun `setTheme transforms current settings`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val expIm = mockk<ExportImportConfigUseCase>(relaxed = true)
        every { repo.appSettings } returns MutableStateFlow(AppSettings())
        val captured = slot<(AppSettings) -> AppSettings>()
        coEvery { repo.updateAppSettings(capture(captured)) } answers {}

        val vm = SettingsViewModel(repo, expIm)
        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()

        val out = captured.captured(AppSettings())
        assertEquals(ThemeMode.DARK, out.themeMode)
    }

    @Test
    fun `export exposes JSON snapshot`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val expIm = mockk<ExportImportConfigUseCase>()
        every { repo.appSettings } returns MutableStateFlow(AppSettings())
        coEvery { expIm.export() } returns "{\"k\":1}"

        val vm = SettingsViewModel(repo, expIm)
        vm.export()
        advanceUntilIdle()

        assertEquals("{\"k\":1}", vm.exportedJson.value)
    }
}
