package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
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

    private lateinit var repository: PodcastRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: AddPodcastFromUrlUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testUrl = "https://example.com/feed.rss"

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        userPreferencesRepository = mockk()
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        useCase = AddPodcastFromUrlUseCase(repository, userPreferencesRepository, dispatcherProvider)
    }

    @Test
    fun `passes autoDownloadLimit and feedUpdateMode from settings to repository`() = runTest(testDispatcher) {
        val settings = UserSettings(autoDownloadLimit = 5, feedUpdateMode = FeedUpdateMode.SMART_STREAM)
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify {
            repository.addPodcast(
                url = testUrl,
                downloadLimit = 5,
                mode = FeedUpdateMode.SMART_STREAM,
                forceFull = false
            )
        }
    }

    @Test
    fun `forceFull is always false regardless of settings`() = runTest(testDispatcher) {
        val settings = UserSettings(feedUpdateMode = FeedUpdateMode.ALWAYS_FULL)
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify { repository.addPodcast(url = testUrl, forceFull = false, mode = any(), downloadLimit = any()) }
    }

    @Test
    fun `uses ALWAYS_FULL mode when set in settings`() = runTest(testDispatcher) {
        val settings = UserSettings(feedUpdateMode = FeedUpdateMode.ALWAYS_FULL, autoDownloadLimit = 3)
        io.mockk.every { userPreferencesRepository.userSettingsFlow } returns flowOf(settings)

        useCase(testUrl)

        coVerify {
            repository.addPodcast(
                url = testUrl,
                downloadLimit = 3,
                mode = FeedUpdateMode.ALWAYS_FULL,
                forceFull = false
            )
        }
    }
}
