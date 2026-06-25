package com.example.pocastcloni.ui.favorites

import app.cash.turbine.test
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.episode.GetFavoriteEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.favorite.ReorderFavoritesUseCase
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getFavoriteEpisodes: GetFavoriteEpisodesWithPodcastInfoUseCase
    private lateinit var audioPlayerController: AudioPlayerController
    private lateinit var toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase
    private lateinit var reorderFavoritesUseCase: ReorderFavoritesUseCase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: FavoritesViewModel

    private val testItem = FavoriteUiItem(
        id = "ep-guid-1",
        episode = EpisodeDisplayModel(
            guid = "ep-guid-1",
            title = "Test Episode",
            description = "Desc",
            podcastRssUrl = "https://example.com/feed.rss",
            podcastTitle = "Test Podcast",
            podcastImageUrl = null,
            isPlayed = false,
            isFavorite = true,
            playbackPositionMs = 0L,
            durationMs = 0L,
            pubDateMs = null,
            datePlayedMs = null
        ),
        podcast = null
    )

    @Before
    fun setup() {
        getFavoriteEpisodes = mockk()
        audioPlayerController = mockk()
        toggleFavoriteEpisodeUseCase = mockk(relaxed = true)
        reorderFavoritesUseCase = mockk(relaxed = true)
        userPreferencesRepository = mockk()

        every { getFavoriteEpisodes() } returns flowOf(emptyList())
        every { audioPlayerController.playerState } returns MutableStateFlow(
            PlayerUiState(currentEpisodeGuid = null, isPlaying = false)
        )
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(UserSettings())

        viewModel = FavoritesViewModel(
            getFavoriteEpisodesWithPodcastInfoUseCase = getFavoriteEpisodes,
            audioPlayerController = audioPlayerController,
            toggleFavoriteEpisodeUseCase = toggleFavoriteEpisodeUseCase,
            reorderFavoritesUseCase = reorderFavoritesUseCase,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @Test
    fun `ToggleEditMode enters edit mode`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial loading
            awaitItem() // loaded

            viewModel.onAction(FavoritesAction.ToggleEditMode)
            val state = awaitItem()
            assertTrue(state.isEditMode)
        }
    }

    @Test
    fun `ToggleEditMode twice exits edit mode`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(FavoritesAction.ToggleEditMode)
            awaitItem()
            viewModel.onAction(FavoritesAction.ToggleEditMode)
            val state = awaitItem()
            assertFalse(state.isEditMode)
        }
    }

    @Test
    fun `OnEpisodeClick calls audioPlayerController when not in edit mode`() = runTest {
        every { audioPlayerController.playerState } returns MutableStateFlow(
            PlayerUiState(currentEpisodeGuid = null, isPlaying = false)
        )
        io.mockk.coEvery { audioPlayerController.play(any()) } returns Unit

        viewModel.onAction(FavoritesAction.OnEpisodeClick("ep-guid-1"))
        advanceUntilIdle()

        coVerify { audioPlayerController.play("ep-guid-1") }
    }

    @Test
    fun `OnEpisodeClick does NOT call audioPlayerController when in edit mode`() = runTest {
        viewModel.onAction(FavoritesAction.ToggleEditMode)
        advanceUntilIdle()

        viewModel.onAction(FavoritesAction.OnEpisodeClick("ep-guid-1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { audioPlayerController.play(any()) }
    }

    @Test
    fun `OnEpisodeSwiped calls toggleFavoriteEpisodeUseCase with isFavorite=true`() = runTest {
        viewModel.onAction(FavoritesAction.OnEpisodeSwiped("ep-guid-1"))
        advanceUntilIdle()

        coVerify { toggleFavoriteEpisodeUseCase("ep-guid-1", true) }
    }

    @Test
    fun `OnEpisodeImageClick sets episodeForDetails`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(FavoritesAction.OnEpisodeImageClick(testItem))
            val state = awaitItem()
            assertNotNull(state.episodeForDetails)
        }
    }

    @Test
    fun `OnDismissEpisodeDetails clears episodeForDetails`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.onAction(FavoritesAction.OnEpisodeImageClick(testItem))
            awaitItem()
            viewModel.onAction(FavoritesAction.OnDismissEpisodeDetails)
            val state = awaitItem()
            assertNull(state.episodeForDetails)
        }
    }
}
