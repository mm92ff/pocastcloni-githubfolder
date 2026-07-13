package com.example.pocastcloni.ui.home.downloads

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.domain.usecase.episode.GetDownloadedEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.StartPlaybackUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.ui.home.detail.DownloadStatusUiModel
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var playerController: AudioPlayerController
    private lateinit var downloader: DownloadEpisodeUseCase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var getDownloadedEpisodes: GetDownloadedEpisodesWithPodcastInfoUseCase
    private lateinit var startPlaybackUseCase: StartPlaybackUseCase
    private lateinit var toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var viewModel: DownloadsViewModel

    private val testEpisode = EpisodeUiModel(
        episodeId = TEST_EPISODE_ID,
        guid = "ep-guid-1",
        podcastUrl = "https://example.com/feed.rss",
        title = "Test Episode",
        podcastTitle = "Test Podcast",
        date = "01.01.2024",
        duration = "45m",
        imageUrl = null,
        downloadStatus = DownloadStatusUiModel.DOWNLOADED,
        downloadProgress = 1.0f,
        isPlayed = false,
        isFavorite = false,
        positionMs = 0L,
        description = null,
        podcastImageUrl = null
    )

    @Before
    fun setup() {
        playerController = mockk()
        downloader = mockk(relaxed = true)
        userPreferencesRepository = mockk()
        getDownloadedEpisodes = mockk()
        startPlaybackUseCase = mockk(relaxed = true)
        toggleFavoriteEpisodeUseCase = mockk(relaxed = true)
        dispatcherProvider = mockk()

        every { playerController.playerState } returns MutableStateFlow(
            PlayerUiState(currentEpisodeId = null, isPlaying = false)
        )
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(UserSettings(confirmDelete = false))
        every { getDownloadedEpisodes() } returns flowOf(emptyList())
        every { dispatcherProvider.io } returns testDispatcher

        viewModel = DownloadsViewModel(
            playerController = playerController,
            downloader = downloader,
            userPreferencesRepository = userPreferencesRepository,
            getDownloadedEpisodesWithPodcastInfo = getDownloadedEpisodes,
            startPlaybackUseCase = startPlaybackUseCase,
            toggleFavoriteEpisodeUseCase = toggleFavoriteEpisodeUseCase,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `deleteEpisode without confirmDelete calls downloader directly`() = runTest(testDispatcher) {
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(UserSettings(confirmDelete = false))

        viewModel.deleteEpisode(testEpisode)
        advanceUntilIdle()

        coVerify { downloader(TEST_EPISODE_ID) }
    }

    private fun buildViewModelWithConfirmDelete(): DownloadsViewModel {
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(UserSettings(confirmDelete = true))
        return DownloadsViewModel(
            playerController = playerController,
            downloader = downloader,
            userPreferencesRepository = userPreferencesRepository,
            getDownloadedEpisodesWithPodcastInfo = getDownloadedEpisodes,
            startPlaybackUseCase = startPlaybackUseCase,
            toggleFavoriteEpisodeUseCase = toggleFavoriteEpisodeUseCase,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `deleteEpisode with confirmDelete=true shows confirmation dialog`() = runTest(testDispatcher) {
        viewModel = buildViewModelWithConfirmDelete()

        viewModel.uiState.test {
            awaitItem() // initial: confirmDelete=false
            awaitItem() // settings loaded: confirmDelete=true

            viewModel.deleteEpisode(testEpisode)
            val state = awaitItem()
            assertNotNull("Confirmation dialog should show episode to delete", state.episodeToDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDelete calls downloader and clears episodeToDelete`() = runTest(testDispatcher) {
        viewModel = buildViewModelWithConfirmDelete()

        viewModel.uiState.test {
            awaitItem() // initial
            awaitItem() // settings loaded: confirmDelete=true

            viewModel.deleteEpisode(testEpisode)
            awaitItem() // episodeToDelete staged

            viewModel.confirmDelete()
            advanceUntilIdle() // downloader coroutine runs

            coVerify { downloader(TEST_EPISODE_ID) }
            val state = expectMostRecentItem()
            assertNull(state.episodeToDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelDelete clears episodeToDelete without deleting`() = runTest(testDispatcher) {
        viewModel = buildViewModelWithConfirmDelete()

        viewModel.uiState.test {
            awaitItem() // initial
            awaitItem() // settings loaded: confirmDelete=true

            viewModel.deleteEpisode(testEpisode)
            awaitItem() // episodeToDelete staged

            viewModel.cancelDelete()
            val state = awaitItem() // episodeToDelete cleared

            coVerify(exactly = 0) { downloader(any()) }
            assertNull(state.episodeToDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `playEpisode calls startPlaybackUseCase with correct episode ID`() = runTest(testDispatcher) {
        viewModel.playEpisode(testEpisode)
        advanceUntilIdle()
        coVerify { startPlaybackUseCase(TEST_EPISODE_ID) }
    }

    @Test
    fun `onFavoriteToggle calls toggleFavoriteEpisodeUseCase`() = runTest(testDispatcher) {
        viewModel.onFavoriteToggle(testEpisode)
        advanceUntilIdle()
        coVerify { toggleFavoriteEpisodeUseCase(TEST_EPISODE_ID, false) }
    }

    private companion object {
        const val TEST_EPISODE_ID = 101L
    }
}
