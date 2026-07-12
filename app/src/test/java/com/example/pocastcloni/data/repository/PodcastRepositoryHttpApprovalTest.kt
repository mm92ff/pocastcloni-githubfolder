package com.example.pocastcloni.data.repository

import android.content.Context
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.manager.PodcastDownloader
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryHttpApprovalTest {
    @Test
    fun `explicit approval activates an imported HTTP stub`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = mockk<PodcastDao>(relaxed = true)
        val syncFeed = mockk<SyncFeedUseCase>(relaxed = true)
        val dispatcherProvider = mockk<DispatcherProvider>().also {
            every { it.io } returns dispatcher
        }
        val url = "http://example.com/feed.xml"
        val stub = PodcastEntity(
            rssUrl = url,
            title = "Imported",
            description = "",
            imageUrl = "",
            allowInsecureHttp = false,
            sortOrder = 4
        )
        coEvery { dao.getPodcastByUrl(url) } returns stub
        val repository = PodcastRepositoryImpl(
            podcastDao = dao,
            itunesSearchApi = mockk<ItunesSearchApi>(relaxed = true),
            dispatcherProvider = dispatcherProvider,
            downloader = mockk<PodcastDownloader>(relaxed = true),
            syncFeedUseCase = Provider { syncFeed },
            context = mockk<Context>(relaxed = true)
        )

        repository.addPodcast(
            url = url,
            downloadLimit = 3,
            mode = FeedUpdateMode.SMART_STREAM,
            allowInsecureHttp = true
        )

        coVerify { dao.updatePodcast(match { it.rssUrl == url && it.allowInsecureHttp }) }
        coVerify {
            syncFeed.invoke(
                url,
                3,
                FeedUpdateMode.SMART_STREAM,
                4,
                false,
                allowInsecureHttp = true,
                allowLocalNetwork = false
            )
        }
    }

    @Test
    fun `explicit local approval activates an imported local stub`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = mockk<PodcastDao>(relaxed = true)
        val syncFeed = mockk<SyncFeedUseCase>(relaxed = true)
        val dispatcherProvider = mockk<DispatcherProvider>().also {
            every { it.io } returns dispatcher
        }
        val url = "https://192.168.1.20/feed.xml"
        val stub = PodcastEntity(
            rssUrl = url,
            title = "Imported",
            description = "",
            imageUrl = "",
            allowLocalNetwork = false,
            sortOrder = 4
        )
        coEvery { dao.getPodcastByUrl(url) } returns stub
        val repository = PodcastRepositoryImpl(
            podcastDao = dao,
            itunesSearchApi = mockk<ItunesSearchApi>(relaxed = true),
            dispatcherProvider = dispatcherProvider,
            downloader = mockk<PodcastDownloader>(relaxed = true),
            syncFeedUseCase = Provider { syncFeed },
            context = mockk<Context>(relaxed = true)
        )

        repository.addPodcast(
            url = url,
            downloadLimit = 3,
            mode = FeedUpdateMode.SMART_STREAM,
            allowLocalNetwork = true
        )

        coVerify { dao.updatePodcast(match { it.rssUrl == url && it.allowLocalNetwork }) }
        coVerify {
            syncFeed.invoke(
                url,
                3,
                FeedUpdateMode.SMART_STREAM,
                4,
                false,
                allowInsecureHttp = false,
                allowLocalNetwork = true
            )
        }
    }
}
