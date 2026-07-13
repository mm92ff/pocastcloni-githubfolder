package com.example.pocastcloni.data.repository

import android.content.Context
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.manager.PodcastDownloader
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import javax.inject.Provider

class PodcastRepositoryCancellationTest {
    @Test
    fun `update all podcasts propagates feed cancellation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = mockk<PodcastDao>()
        val syncFeed = mockk<SyncFeedUseCase>()
        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.io } returns dispatcher
        coEvery { dao.getAllPodcastUrls() } returns listOf("https://example.com/feed.xml")
        coEvery {
            syncFeed.invoke(
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
            PodcastRepositoryImpl(
                podcastDao = dao,
                itunesSearchApi = mockk<ItunesSearchApi>(),
                dispatcherProvider = dispatcherProvider,
                downloader = mockk<PodcastDownloader>(),
                syncFeedUseCase = Provider { syncFeed },
                context = mockk<Context>()
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
