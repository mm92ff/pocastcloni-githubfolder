package com.example.pocastcloni.ui.favorites

import app.cash.turbine.test
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.episode.GetFavoriteEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.favorite.ReorderFavoritesUseCase
import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
            datePlayedMs = null,
            favoriteAddedAtMs = null
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
    fun `default sort mode is manual`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()

            assertEquals(FavoritesSortMode.MANUAL, state.sortMode)
        }
    }

    @Test
    fun `ChangeSortMode AddedDate exits edit mode and exposes grouped rows`() = runTest {
        val today = episodeWithPodcastInfo("today", favoriteAddedAtMs = daysAgo(0))
        val yesterday = episodeWithPodcastInfo("yesterday", favoriteAddedAtMs = daysAgo(1))
        every { getFavoriteEpisodes() } returns flowOf(listOf(yesterday, today))

        viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            val loaded = awaitItem()
            viewModel.onAction(FavoritesAction.ToggleEditMode)
            val editState = awaitItem()
            assertTrue(editState.isEditMode)

            viewModel.onAction(FavoritesAction.ChangeSortMode(FavoritesSortMode.ADDED_DATE))
            val addedState = awaitItem()

            assertEquals(FavoritesSortMode.ADDED_DATE, addedState.sortMode)
            assertFalse(addedState.isEditMode)
            val todayItem = loaded.favorites.first { it.id == "today" }
            val yesterdayItem = loaded.favorites.first { it.id == "yesterday" }
            assertEquals(
                listOf(
                    FavoriteListRow.SectionHeader(DateBucket.TODAY),
                    FavoriteListRow.EpisodeRow(todayItem),
                    FavoriteListRow.SectionHeader(DateBucket.YESTERDAY),
                    FavoriteListRow.EpisodeRow(yesterdayItem)
                ),
                addedState.dateGroupedRows
            )
        }
    }

    @Test
    fun `OnReorder is ignored in added date mode`() = runTest {
        viewModel.onAction(FavoritesAction.ChangeSortMode(FavoritesSortMode.ADDED_DATE))
        viewModel.onAction(FavoritesAction.OnReorder(0, 1))
        advanceUntilIdle()

        coVerify(exactly = 0) { reorderFavoritesUseCase(any()) }
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

    private fun createViewModel(): FavoritesViewModel =
        FavoritesViewModel(
            getFavoriteEpisodesWithPodcastInfoUseCase = getFavoriteEpisodes,
            audioPlayerController = audioPlayerController,
            toggleFavoriteEpisodeUseCase = toggleFavoriteEpisodeUseCase,
            reorderFavoritesUseCase = reorderFavoritesUseCase,
            userPreferencesRepository = userPreferencesRepository
        )

    private fun daysAgo(daysAgo: Long): Long =
        LocalDate.now(ZoneId.systemDefault())
            .minusDays(daysAgo)
            .atTime(10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun episodeWithPodcastInfo(
        guid: String,
        favoriteAddedAtMs: Long?
    ): EpisodeWithPodcastInfo =
        EpisodeWithPodcastInfo(
            episode =
            EpisodePresentation(
                guid = guid,
                title = "Episode $guid",
                link = null,
                description = "",
                podcastRssUrl = "https://example.com/feed.xml",
                isFavorite = true,
                isPlayed = false,
                playbackPositionMs = 0L,
                durationMs = 0L,
                pubDateMs = null,
                datePlayedMs = null,
                favoriteAddedAtMs = favoriteAddedAtMs,
                downloadStatus = DownloadStatus.NOT_DOWNLOADED
            ),
            podcast =
            Podcast(
                rssUrl = "https://example.com/feed.xml",
                title = "Podcast",
                description = "",
                imageUrl = "",
                lastRefreshed = Date(0L),
                autoDownloadEnabled = false,
                sortOrder = 0L,
                hasNewEpisodes = false,
                lastModifiedHeader = null,
                eTagHeader = null,
                latestEpisodeDate = null,
                isLatestEpisodePlayed = null
            )
        )
}
