package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.FeedUpdateRunner
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val repository = mockk<FeedUpdateRunner>(relaxed = true)
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
        givenSmartSettings(smartStreamItemLimit = 10)
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            repository.updateAllPodcasts(
                0,
                FeedUpdateMode.SMART_STREAM,
                true,
                feedItemLimit = Constants.SecurityLimits.MAX_FEED_ITEMS
            )
        } returns expected

        val result =
            coordinator().refresh(
                source = FeedRefreshSource.MANUAL,
                forceFull = true,
                downloadLimitOverride = 0
            )

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            repository.updateAllPodcasts(
                0,
                FeedUpdateMode.SMART_STREAM,
                true,
                feedItemLimit = Constants.SecurityLimits.MAX_FEED_ITEMS
            )
        }
    }

    @Test
    fun `limited smart refresh passes the configured feed prefix`() = runTest(dispatcher) {
        givenSmartSettings(smartStreamItemLimit = 10)
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 10
            )
        } returns expected

        assertEquals(expected, coordinator().refresh(FeedRefreshSource.BACKGROUND))

        coVerify(exactly = 1) {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 10
            )
        }
    }

    @Test
    fun `smart refreshes with different feed limits use separate flights`() = runTest(dispatcher) {
        val settings = MutableStateFlow(
            UserSettings(
                autoDownloadLimit = 3,
                feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                smartStreamItemLimit = 10
            )
        )
        every { preferences.userSettingsFlow } returns settings
        every { dispatcherProvider.io } returns dispatcher
        val firstGate = CompletableDeferred<Unit>()
        val firstSummary = PodcastUpdateSummary(1, 1, 0)
        val secondSummary = PodcastUpdateSummary(2, 2, 0)
        coEvery {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 10
            )
        } coAnswers {
            firstGate.await()
            firstSummary
        }
        coEvery {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 5
            )
        } returns secondSummary
        val coordinator = coordinator()

        val first = async { coordinator.refresh(FeedRefreshSource.STARTUP) }
        runCurrent()
        settings.value = settings.value.copy(smartStreamItemLimit = 5)
        val second = async { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
        runCurrent()

        coVerify(exactly = 0) {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 5
            )
        }
        firstGate.complete(Unit)
        runCurrent()

        assertEquals(firstSummary, first.await())
        assertEquals(secondSummary, second.await())
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

    @Test
    fun `backup restore with smart zero forces a full refresh`() = runTest(dispatcher) {
        givenSmartSettings()
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, true) } returns expected

        val result = coordinator().refresh(FeedRefreshSource.BACKUP_RESTORE)

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, true)
        }
    }

    @Test
    fun `backup restore with a smart limit uses the configured feed prefix`() = runTest(dispatcher) {
        givenSmartSettings(smartStreamItemLimit = 10)
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 10
            )
        } returns expected

        val result = coordinator().refresh(FeedRefreshSource.BACKUP_RESTORE)

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.SMART_STREAM,
                false,
                feedItemLimit = 10
            )
        }
    }

    @Test
    fun `backup restore in always full mode remains full`() = runTest(dispatcher) {
        every { preferences.userSettingsFlow } returns
            flowOf(
                UserSettings(
                    autoDownloadLimit = 3,
                    feedUpdateMode = FeedUpdateMode.ALWAYS_FULL,
                    smartStreamItemLimit = 10
                )
            )
        every { dispatcherProvider.io } returns dispatcher
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.ALWAYS_FULL,
                true,
                feedItemLimit = Constants.SecurityLimits.MAX_FEED_ITEMS
            )
        } returns expected

        val result = coordinator().refresh(FeedRefreshSource.BACKUP_RESTORE)

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            repository.updateAllPodcasts(
                3,
                FeedUpdateMode.ALWAYS_FULL,
                true,
                feedItemLimit = Constants.SecurityLimits.MAX_FEED_ITEMS
            )
        }
    }

    @Test
    fun `targeted refresh preserves the URL filter through the coordinator`() = runTest(dispatcher) {
        givenSmartSettings()
        val feedUrls = setOf("https://example.com/retry.xml")
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false, feedUrls)
        } returns expected

        val result =
            coordinator().refresh(
                source = FeedRefreshSource.BACKGROUND,
                feedUrls = feedUrls
            )

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false, feedUrls)
        }
    }

    private fun givenSmartSettings(
        smartStreamItemLimit: Int = Constants.Preferences.DEFAULT_SMART_STREAM_ITEM_LIMIT
    ) {
        every { preferences.userSettingsFlow } returns
            flowOf(
                UserSettings(
                    autoDownloadLimit = 3,
                    feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                    smartStreamItemLimit = smartStreamItemLimit
                )
            )
        every { dispatcherProvider.io } returns dispatcher
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator() =
        FeedRefreshCoordinator(repository, preferences, backgroundScope, dispatcherProvider)
}
