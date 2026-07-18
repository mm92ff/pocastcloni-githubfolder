package com.example.pocastcloni.ui.home.add

import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.podcast.AddPodcastFromUrlUseCase
import com.example.pocastcloni.playback.api.PlaybackState
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.playback.api.PlayerUiState
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AddPodcastViewModelLanguageTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `unexpected subscription error uses stable English resource`() = runTest(dispatcher) {
        val addPodcast = mockk<AddPodcastFromUrlUseCase>()
        val failure = IllegalStateException("Podcast konnte nicht geladen werden")
        coEvery { addPodcast(any(), any(), any()) } throws failure
        val viewModel = createViewModel(addPodcast)
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        var loggedThrowable: Throwable? = null
        val recordingTree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?
                ) {
                    loggedThrowable = t
                }
            }
        Timber.plant(recordingTree)

        try {
            viewModel.onTogglePodcast(
                PodcastSearchResult(
                    feedUrl = "https://example.com/feed.xml",
                    artworkUrl = "",
                    title = "Example",
                    artist = "Publisher"
                )
            )
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.error_add_podcast_failed),
                viewModel.uiState.value.searchError
            )
            assertSame(failure, loggedThrowable)
        } finally {
            collector.cancel()
            Timber.uproot(recordingTree)
        }
    }

    @Test
    fun `subscription cancellation does not become a user facing error`() = runTest(dispatcher) {
        val addPodcast = mockk<AddPodcastFromUrlUseCase>()
        coEvery { addPodcast(any(), any(), any()) } throws CancellationException("cancelled")
        val viewModel = createViewModel(addPodcast)
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

        viewModel.onTogglePodcast(
            PodcastSearchResult(
                feedUrl = "https://example.com/feed.xml",
                artworkUrl = "",
                title = "Example",
                artist = "Publisher"
            )
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.searchError)
        collector.cancel()
    }

    private fun createViewModel(addPodcast: AddPodcastFromUrlUseCase): AddPodcastViewModel {
        val podcastQuery = mockk<PodcastQueryPort>()
        every { podcastQuery.getSubscribedUrlsFlow() } returns flowOf(emptyList())
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        val playerState = mockk<PlayerStatePort>()
        every { playerState.playerState } returns MutableStateFlow(PlayerUiState())
        every { playerState.playbackState } returns MutableStateFlow(PlaybackState())
        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.main } returns dispatcher
        every { dispatcherProvider.io } returns dispatcher
        every { dispatcherProvider.default } returns dispatcher

        return AddPodcastViewModel(
            podcastQuery = podcastQuery,
            userPreferencesRepository = preferences,
            playerStatePort = playerState,
            addPodcastFromUrl = addPodcast,
            searchPodcasts = mockk(relaxed = true),
            dispatcherProvider = dispatcherProvider,
            removePodcastSubscription = mockk(relaxed = true)
        )
    }
}
