package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AddPodcastFromUrlHttpApprovalTest {
    @Test
    fun `explicit approval activates an imported HTTP stub`() = runTest {
        val url = "http://example.com/feed.xml"
        val fixture = fixture(
            url = url,
            dispatcher = StandardTestDispatcher(testScheduler),
            allowInsecureHttp = false
        )

        fixture.useCase(url, allowInsecureHttp = true)

        coVerify {
            fixture.feedSyncStore.approvePodcastNetworkAccess(
                rssUrl = url,
                allowInsecureHttp = true,
                allowLocalNetwork = false
            )
        }
        coVerify {
            fixture.feedSyncRunner.sync(
                url = url,
                downloadLimit = 3,
                mode = FeedUpdateMode.SMART_STREAM,
                sortOrder = 4,
                forceFull = false,
                allowInsecureHttp = true,
                allowLocalNetwork = false
            )
        }
    }

    @Test
    fun `explicit local approval activates an imported local stub`() = runTest {
        val url = "https://192.168.1.20/feed.xml"
        val fixture = fixture(
            url = url,
            dispatcher = StandardTestDispatcher(testScheduler),
            allowLocalNetwork = false
        )

        fixture.useCase(url, allowLocalNetwork = true)

        coVerify {
            fixture.feedSyncStore.approvePodcastNetworkAccess(
                rssUrl = url,
                allowInsecureHttp = false,
                allowLocalNetwork = true
            )
        }
        coVerify {
            fixture.feedSyncRunner.sync(
                url = url,
                downloadLimit = 3,
                mode = FeedUpdateMode.SMART_STREAM,
                sortOrder = 4,
                forceFull = false,
                allowInsecureHttp = false,
                allowLocalNetwork = true
            )
        }
    }

    private fun fixture(
        url: String,
        dispatcher: TestDispatcher,
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false
    ): Fixture {
        val podcastQuery = mockk<PodcastQueryPort>()
        val feedSyncStore = mockk<FeedSyncStore>(relaxed = true)
        val feedSyncRunner = mockk<FeedSyncRunner>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns
            flowOf(UserSettings(autoDownloadLimit = 3, feedUpdateMode = FeedUpdateMode.SMART_STREAM))
        coEvery { podcastQuery.getPodcast(url) } returns
            Podcast(
                rssUrl = url,
                title = "Imported",
                description = "",
                imageUrl = "",
                lastRefreshed = Date(0),
                autoDownloadEnabled = false,
                sortOrder = 4,
                hasNewEpisodes = false,
                lastModifiedHeader = null,
                eTagHeader = null,
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork
            )
        return Fixture(
            useCase = AddPodcastFromUrlUseCase(
                podcastQuery,
                feedSyncStore,
                feedSyncRunner,
                preferences,
                dispatcherProvider
            ),
            feedSyncStore = feedSyncStore,
            feedSyncRunner = feedSyncRunner
        )
    }

    private data class Fixture(
        val useCase: AddPodcastFromUrlUseCase,
        val feedSyncStore: FeedSyncStore,
        val feedSyncRunner: FeedSyncRunner
    )
}
