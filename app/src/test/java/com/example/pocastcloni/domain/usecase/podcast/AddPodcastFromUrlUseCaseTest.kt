package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddPodcastFromUrlUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var podcastQuery: PodcastQueryPort
    private lateinit var feedSyncStore: FeedSyncStore
    private lateinit var feedSyncRunner: FeedSyncRunner
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: AddPodcastFromUrlUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testUrl = "https://example.com/feed.rss"

    @Before
    fun setup() {
        podcastQuery = mockk(relaxed = true)
        feedSyncStore = mockk(relaxed = true)
        feedSyncRunner = mockk(relaxed = true)
        io.mockk.coEvery { podcastQuery.getPodcast(testUrl) } returns null
        userPreferencesRepository = mockk()
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        useCase = AddPodcastFromUrlUseCase(
            podcastQuery,
            feedSyncStore,
            feedSyncRunner,
            userPreferencesRepository,
            dispatcherProvider
        )
    }

    @Test
    fun `passes smart stream settings to feed sync`() = runTest(testDispatcher) {
        val settings =
            UserSettings(
                autoDownloadLimit = 5,
                feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                smartStreamItemLimit = 10
            )
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify {
            feedSyncRunner.sync(
                url = testUrl,
                downloadLimit = 5,
                mode = FeedUpdateMode.SMART_STREAM,
                forceFull = false,
                feedItemLimit = 10
            )
        }
    }

    @Test
    fun `forceFull is always false regardless of settings`() = runTest(testDispatcher) {
        val settings = UserSettings(feedUpdateMode = FeedUpdateMode.ALWAYS_FULL)
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify { feedSyncRunner.sync(url = testUrl, forceFull = false, mode = any(), downloadLimit = any()) }
    }

    @Test
    fun `uses ALWAYS_FULL mode when set in settings`() = runTest(testDispatcher) {
        val settings = UserSettings(feedUpdateMode = FeedUpdateMode.ALWAYS_FULL, autoDownloadLimit = 3)
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify {
            feedSyncRunner.sync(
                url = testUrl,
                downloadLimit = 3,
                mode = FeedUpdateMode.ALWAYS_FULL,
                forceFull = false,
                feedItemLimit = Constants.SecurityLimits.MAX_FEED_ITEMS
            )
        }
    }
}
