package com.kahaf.guardianshield.viewmodels

import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.presentation.applist.AppListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    @Test
    fun `setQuery updates query state`() = runTest {
        val repo = mockk<AppRuleRepository>(relaxed = true)
        every { repo.observeAll() } returns MutableStateFlow(emptyList())
        coEvery { repo.getInstalledApps(any()) } returns emptyList()

        val vm = AppListViewModel(repo)
        vm.setQuery("instagram")
        assertEquals("instagram", vm.query.value)
    }

    @Test
    fun `setState delegates to repository`() = runTest {
        val repo = mockk<AppRuleRepository>(relaxed = true)
        every { repo.observeAll() } returns MutableStateFlow(emptyList())
        coEvery { repo.getInstalledApps(any()) } returns emptyList()

        val vm = AppListViewModel(repo)
        vm.setState("com.x", AppRuleState.BLOCKED)
        advanceUntilIdle()
        coVerify { repo.setState("com.x", AppRuleState.BLOCKED) }
    }
}
