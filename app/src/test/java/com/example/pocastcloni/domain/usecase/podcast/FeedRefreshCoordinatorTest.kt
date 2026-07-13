package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedRefreshCoordinatorTest {
    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>()
    private val dispatcherProvider = mockk<DispatcherProvider>()
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `concurrent configured refreshes share one repository run`() = runTest(dispatcher) {
        givenSmartSettings()
        val gate = CompletableDeferred<Unit>()
        val summary = PodcastUpdateSummary(2, 2, 0)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            gate.await()
            summary
        }
        val coordinator = coordinator()

        val startup = async { coordinator.refresh(FeedRefreshSource.STARTUP) }
        runCurrent()
        val background = async { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
        runCurrent()

        coVerify(exactly = 1) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) }
        gate.complete(Unit)
        assertEquals(summary, startup.await())
        assertEquals(summary, background.await())
    }

    @Test
    fun `manual refresh during smart refresh queues one full run`() = runTest(dispatcher) {
        givenSmartSettings()
        val smartGate = CompletableDeferred<Unit>()
        val fullGate = CompletableDeferred<Unit>()
        val smartSummary = PodcastUpdateSummary(2, 2, 0)
        val fullSummary = PodcastUpdateSummary(2, 1, 1)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            smartGate.await()
            smartSummary
        }
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, true) } coAnswers {
            fullGate.await()
            fullSummary
        }
        val coordinator = coordinator()

        val smart = async { coordinator.refresh(FeedRefreshSource.STARTUP) }
        runCurrent()
        val firstManual = async { coordinator.refresh(FeedRefreshSource.MANUAL) }
        val secondManual = async { coordinator.refresh(FeedRefreshSource.MANUAL) }
        runCurrent()
        coVerify(exactly = 0) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, true) }
        firstManual.cancelAndJoin()

        smartGate.complete(Unit)
        runCurrent()
        coVerify(exactly = 1) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, true) }
        fullGate.complete(Unit)

        assertEquals(smartSummary, smart.await())
        assertTrue(firstManual.isCancelled)
        assertEquals(fullSummary, secondManual.await())
    }

    @Test
    fun `repository failure reaches all waiters and coordinator remains reusable`() = runTest(dispatcher) {
        givenSmartSettings()
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            gate.await()
            throw IllegalStateException("network")
        }
        val coordinator = coordinator()
        val first = async { runCatching { coordinator.refresh(FeedRefreshSource.STARTUP) } }
        val second = async { runCatching { coordinator.refresh(FeedRefreshSource.BACKGROUND) } }
        runCurrent()
        gate.complete(Unit)
        assertEquals("network", first.await().exceptionOrNull()?.message)
        assertEquals("network", second.await().exceptionOrNull()?.message)

        val recovered = PodcastUpdateSummary(1, 1, 0)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } returns recovered
        assertEquals(recovered, coordinator.refresh(FeedRefreshSource.STARTUP))
        coVerify(exactly = 2) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) }
    }

    @Test
    fun `coordinator never performs episode cleanup`() = runTest(dispatcher) {
        givenSmartSettings()
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } returns
            PodcastUpdateSummary(1, 1, 0)

        coordinator().refresh(FeedRefreshSource.BACKGROUND)

        coVerify(exactly = 0) { repository.cleanupPlayedEpisodes() }
        coVerify(exactly = 0) { repository.pruneLibrary(any()) }
    }

    @Test
    fun `cancelling the last waiter cancels work and coordinator remains reusable`() = runTest(dispatcher) {
        givenSmartSettings()
        val underlyingCancelled = CompletableDeferred<Unit>()
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                underlyingCancelled.complete(Unit)
            }
        }
        val coordinator = coordinator()

        val request = async { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
        runCurrent()
        request.cancelAndJoin()
        runCurrent()
        assertTrue(underlyingCancelled.isCompleted)

        val recovered = PodcastUpdateSummary(1, 1, 0)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } returns recovered
        assertEquals(recovered, coordinator.refresh(FeedRefreshSource.STARTUP))
    }

    @Test
    fun `new request waits until cancelling flight has fully unwound`() = runTest(dispatcher) {
        givenSmartSettings()
        val cancelStarted = CompletableDeferred<Unit>()
        val allowCancellationToFinish = CompletableDeferred<Unit>()
        val replacementResult = PodcastUpdateSummary(1, 1, 0)
        var invocation = 0
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            invocation += 1
            if (invocation == 1) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cancelStarted.complete(Unit)
                        allowCancellationToFinish.await()
                    }
                }
            }
            replacementResult
        }
        val coordinator = coordinator()

        val cancelledRequest = async { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
        runCurrent()
        cancelledRequest.cancel()
        runCurrent()
        cancelStarted.await()

        val replacement = async { coordinator.refresh(FeedRefreshSource.STARTUP) }
        runCurrent()
        coVerify(exactly = 1) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) }

        allowCancellationToFinish.complete(Unit)
        runCurrent()
        assertEquals(replacementResult, replacement.await())
        coVerify(exactly = 2) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) }
    }

    @Test
    fun `settings manual refresh preserves no-download policy`() = runTest(dispatcher) {
        givenSmartSettings()
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery { repository.updateAllPodcasts(0, FeedUpdateMode.SMART_STREAM, true) } returns expected

        val result =
            coordinator().refresh(
                source = FeedRefreshSource.MANUAL,
                forceFull = true,
                downloadLimitOverride = 0
            )

        assertEquals(expected, result)
        coVerify(exactly = 1) { repository.updateAllPodcasts(0, FeedUpdateMode.SMART_STREAM, true) }
    }

    @Test
    fun `cancelling one shared active waiter keeps work for remaining waiter`() = runTest(dispatcher) {
        givenSmartSettings()
        val gate = CompletableDeferred<Unit>()
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } coAnswers {
            gate.await()
            expected
        }
        val coordinator = coordinator()

        val first = async { coordinator.refresh(FeedRefreshSource.STARTUP) }
        val second = async { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()

        coVerify(exactly = 1) { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) }
        gate.complete(Unit)
        assertEquals(expected, second.await())
    }

    private fun givenSmartSettings() {
        every { preferences.userSettingsFlow } returns
            flowOf(UserSettings(autoDownloadLimit = 3, feedUpdateMode = FeedUpdateMode.SMART_STREAM))
        every { dispatcherProvider.io } returns dispatcher
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator() =
        FeedRefreshCoordinator(repository, preferences, backgroundScope, dispatcherProvider)
}
