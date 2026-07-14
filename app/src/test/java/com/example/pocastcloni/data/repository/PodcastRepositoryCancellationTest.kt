package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.sync.FeedUpdateOrchestrator
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryCancellationTest {
    @Test
    fun `update all podcasts waits for each bounded batch before launching the next`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val podcastQuery = mockk<PodcastQueryPort>()
        val syncFeed = mockk<FeedSyncRunner>()
        val dispatcherProvider = mockk<DispatcherProvider>()
        val urls = List(5) { "https://example.com/feed-$it.xml" }
        val gates = urls.associateWith { CompletableDeferred<Unit>() }
        var started = 0
        every { dispatcherProvider.io } returns dispatcher
        coEvery { podcastQuery.getSubscribedUrls() } returns urls
        coEvery {
            syncFeed.sync(any(), 3, FeedUpdateMode.SMART_STREAM, null, true, false, false)
        } coAnswers {
            started += 1
            gates.getValue(firstArg()).await()
        }
        val repository =
            FeedUpdateOrchestrator(
                podcastQuery = podcastQuery,
                feedSyncRunner = syncFeed,
                dispatcherProvider = dispatcherProvider
            )

        val refresh = async {
            repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, forceFull = true)
        }
        runCurrent()
        assertEquals(4, started)

        gates.getValue(urls.first()).complete(Unit)
        runCurrent()
        assertEquals(4, started)

        urls.drop(1).take(3).forEach { gates.getValue(it).complete(Unit) }
        runCurrent()
        assertEquals(5, started)
        gates.getValue(urls.last()).complete(Unit)

        assertEquals(5, refresh.await().successfulCount)
    }

    @Test
    fun `update all podcasts propagates feed cancellation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val podcastQuery = mockk<PodcastQueryPort>()
        val syncFeed = mockk<FeedSyncRunner>()
        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.io } returns dispatcher
        coEvery { podcastQuery.getSubscribedUrls() } returns listOf("https://example.com/feed.xml")
        coEvery {
            syncFeed.sync(
                "https://example.com/feed.xml",
                3,
                FeedUpdateMode.SMART_STREAM,
                null,
                false,
                false,
                false
            )
        } throws CancellationException("cancelled")
        val repository =
            FeedUpdateOrchestrator(
                podcastQuery = podcastQuery,
                feedSyncRunner = syncFeed,
                dispatcherProvider = dispatcherProvider
            )

        try {
            repository.updateAllPodcasts(
                downloadLimit = 3,
                mode = FeedUpdateMode.SMART_STREAM,
                forceFull = false
            )
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
            Unit
        }
    }
}
