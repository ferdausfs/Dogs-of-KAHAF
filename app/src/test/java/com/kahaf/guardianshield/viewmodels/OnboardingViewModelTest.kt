package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.data.permissions.PermissionManager
import com.kahaf.guardianshield.presentation.onboarding.OnboardingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.1.1 FIX: the `PermissionManager.Snapshot` data-class gained two new
 * fields in the legacy merge (`deviceAdmin`, `autoRevokeDisabled`). The old
 * constructor calls in this test no longer compiled, which broke
 * `./gradlew testDebugUnitTest` for the entire project.
 */
class OnboardingViewModelTest {

    @Test
    fun `init refreshes permission snapshot`() {
        val pm = mockk<PermissionManager>(relaxed = true)
        every { pm.refresh() } returns PermissionManager.Snapshot(
            accessibility = true,
            overlay = true,
            notifications = true,
            ignoreBatteryOpt = false,
            deviceAdmin = false,
            autoRevokeDisabled = true,
            capturedAtMs = 0L
        )
        val vm = OnboardingViewModel(pm)
        verify { pm.refresh() }
        assertTrue(vm.state.value.canFinish)
    }

    @Test
    fun `canFinish requires accessibility and overlay`() {
        val pm = mockk<PermissionManager>(relaxed = true)
        every { pm.refresh() } returns PermissionManager.Snapshot(
            accessibility = false,
            overlay = true,
            notifications = true,
            ignoreBatteryOpt = true,
            deviceAdmin = false,
            autoRevokeDisabled = true,
            capturedAtMs = 0L
        )
        val vm = OnboardingViewModel(pm)
        assertTrue(!vm.state.value.canFinish)
    }
}
