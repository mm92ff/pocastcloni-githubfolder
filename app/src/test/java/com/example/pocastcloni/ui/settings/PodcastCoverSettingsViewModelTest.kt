package com.example.pocastcloni.ui.settings

import com.example.pocastcloni.data.cover.PodcastCoverMaintenance
import com.example.pocastcloni.data.cover.PodcastCoverRefreshSummary
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastCoverSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `rapid refresh taps start one materialization and release claim after completion`() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val maintenance = mockk<PodcastCoverMaintenance>()
        coEvery { maintenance.refreshAllNow() } coAnswers {
            completion.await()
            PodcastCoverRefreshSummary(successfulCount = 2, failureCount = 0)
        }
        val dispatchers = mockk<DispatcherProvider>()
        every { dispatchers.io } returns dispatcher
        val viewModel = PodcastCoverSettingsViewModel(maintenance, dispatchers)

        viewModel.refreshCovers()
        viewModel.refreshCovers()
        runCurrent()

        assertTrue(viewModel.state.value.isRefreshing)
        coVerify(exactly = 1) { maintenance.refreshAllNow() }

        completion.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        coVerify(exactly = 1) { maintenance.refreshAllNow() }
    }
}
