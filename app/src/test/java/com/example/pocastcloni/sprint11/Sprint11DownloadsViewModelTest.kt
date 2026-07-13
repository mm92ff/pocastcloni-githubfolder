package com.example.pocastcloni.sprint11

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.domain.usecase.episode.GetDownloadedEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.StartPlaybackUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.ui.home.downloads.DownloadsViewModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint11DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `retry resubscribes and clears retained download error`() = runTest(dispatcher) {
        val getDownloads = mockk<GetDownloadedEpisodesWithPodcastInfoUseCase>()
        var subscriptions = 0
        every { getDownloads() } answers {
            subscriptions++
            if (subscriptions == 1) {
                flow<List<EpisodeWithPodcastInfo>> {
                    emit(emptyList())
                    throw IllegalStateException("downloads unavailable")
                }
            } else {
                flowOf(emptyList())
            }
        }
        val playerController = mockk<AudioPlayerController>()
        every { playerController.playerState } returns MutableStateFlow(PlayerUiState())
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())

        val viewModel =
            DownloadsViewModel(
                playerController = playerController,
                downloader = mockk<DownloadEpisodeUseCase>(relaxed = true),
                userPreferencesRepository = preferences,
                getDownloadedEpisodesWithPodcastInfo = getDownloads,
                startPlaybackUseCase = mockk<StartPlaybackUseCase>(relaxed = true),
                toggleFavoriteEpisodeUseCase = mockk<ToggleFavoriteEpisodeUseCase>(relaxed = true),
                dispatcherProvider = mockk<DispatcherProvider> {
                    every { io } returns dispatcher
                }
            )

        viewModel.uiState.test {
            awaitItem()
            assertNull(awaitItem().contentLoad.error)

            val failed = awaitItem()
            assertNotNull(failed.contentLoad.error)
            assertEquals(emptyList<Any>(), failed.contentLoad.lastValue)

            viewModel.retryDownloads()

            val recovered = awaitItem()
            assertNull(recovered.contentLoad.error)
            assertEquals(emptyList<Any>(), recovered.contentLoad.lastValue)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, subscriptions)
    }
}

