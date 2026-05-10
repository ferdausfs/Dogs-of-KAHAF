package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.data.classifier.TfLiteNsfwClassifier
import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.presentation.aisettings.AiSettingsViewModel
import io.mockk.coEvery
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

    private fun mocks(): Triple<SettingsRepository, AppRuleRepository, TfLiteNsfwClassifier> {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val appRepo = mockk<AppRuleRepository>(relaxed = true)
        val classifier = mockk<TfLiteNsfwClassifier>(relaxed = true)
        every { repo.aiSettings } returns MutableStateFlow(AiSettings())
        every { classifier.isModelLoaded } returns MutableStateFlow(false)
        coEvery { appRepo.getInstalledApps(any()) } returns emptyList()
        return Triple(repo, appRepo, classifier)
    }

    @Test
    fun `toggleSource adds package to set`() = runTest {
        val (repo, appRepo, classifier) = mocks()
        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo, classifier)
        vm.toggleSource("com.example.app", true)
        advanceUntilIdle()

        val out = captured.captured(AiSettings(contentSourcePackages = emptySet()))
        assertTrue("com.example.app" in out.contentSourcePackages)
    }

    @Test
    fun `setSensitivity persists value`() = runTest {
        val (repo, appRepo, classifier) = mocks()
        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo, classifier)
        vm.setSensitivity(0.8f)
        advanceUntilIdle()

        val out = captured.captured(AiSettings(sensitivity = 0.1f))
        assertEquals(0.8f, out.sensitivity, 0.0001f)
    }

    @Test
    fun `setMinImageSize clamps to 50_500`() = runTest {
        val (repo, appRepo, classifier) = mocks()
        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo, classifier)
        vm.setMinImageSize(9999)
        advanceUntilIdle()

        val out = captured.captured(AiSettings())
        assertEquals(500, out.minImageSize)
    }

    @Test
    fun `setModelInputNormalized toggles flag`() = runTest {
        val (repo, appRepo, classifier) = mocks()
        val captured = slot<(AiSettings) -> AiSettings>()
        coEvery { repo.updateAiSettings(capture(captured)) } answers {}

        val vm = AiSettingsViewModel(repo, appRepo, classifier)
        vm.setModelInputNormalized(true)
        advanceUntilIdle()

        val out = captured.captured(AiSettings())
        assertTrue(out.modelInputNormalized)
    }
}
