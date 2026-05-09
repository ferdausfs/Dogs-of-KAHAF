package com.kahaf.guardianshield.viewmodels

import app.cash.turbine.test
import com.kahaf.guardianshield.domain.model.KeywordRule
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import com.kahaf.guardianshield.presentation.keywords.KeywordsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeywordsViewModelTest {

    @get:Rule val coroutineRule = MainCoroutineRule()

    @Test
    fun `add keyword stores via repository`() = runTest {
        val repo = mockk<KeywordRepository>(relaxed = true)
        val flow = MutableStateFlow<List<KeywordRule>>(emptyList())
        every { repo.observeAll() } returns flow
        coEvery { repo.add(any(), any()) } returns 1L

        val vm = KeywordsViewModel(repo)
        vm.add("badword", false)
        advanceUntilIdle()

        coVerify { repo.add("badword", false) }
        assertNull(vm.error.value)
    }

    @Test
    fun `invalid regex sets error message`() = runTest {
        val repo = mockk<KeywordRepository>(relaxed = true)
        every { repo.observeAll() } returns MutableStateFlow(emptyList())
        coEvery { repo.add(any(), true) } throws IllegalArgumentException("Invalid regex")

        val vm = KeywordsViewModel(repo)
        vm.add("[unbalanced", true)
        advanceUntilIdle()

        assertEquals("Invalid regex", vm.error.value)
    }

    @Test
    fun `rules flow exposes repository data`() = runTest {
        val repo = mockk<KeywordRepository>(relaxed = true)
        val seed = listOf(KeywordRule(1L, "x", false, 0L))
        every { repo.observeAll() } returns MutableStateFlow(seed)

        val vm = KeywordsViewModel(repo)
        vm.rules.test {
            // initial value from stateIn is empty, then collected value
            val first = awaitItem()
            // either empty (initial) or seed; advance and check final
            if (first.isEmpty()) assertEquals(seed, awaitItem()) else assertEquals(seed, first)
            cancelAndConsumeRemainingEvents()
        }
    }
}
