package com.example.pocastcloni.ui.history

import app.cash.turbine.test
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.episode.GetPlaybackHistoryWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.history.ClearHistoryUseCase
import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getPlaybackHistoryWithPodcastInfoUseCase: GetPlaybackHistoryWithPodcastInfoUseCase
    private lateinit var audioPlayerController: AudioPlayerController
    private lateinit var clearHistoryUseCase: ClearHistoryUseCase
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        getPlaybackHistoryWithPodcastInfoUseCase = mockk()
        audioPlayerController = mockk()
        clearHistoryUseCase = mockk(relaxed = true)
        userPreferencesRepository = mockk()

        // Setup default mocks
        every { getPlaybackHistoryWithPodcastInfoUseCase() } returns flowOf(emptyList())
        every { audioPlayerController.playerState } returns MutableStateFlow(
            PlayerUiState(currentEpisodeGuid = null, isPlaying = false)
        )
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(
            UserSettings(
                layoutMode = LayoutMode.LIST,
                gridSize = 2,
                showGridTitles = true,
                oneHandedMode = false,
                navBarHeight = 80,
                progressBarHeight = 4
            )
        )

        viewModel = HistoryViewModel(
            getPlaybackHistoryWithPodcastInfoUseCase = getPlaybackHistoryWithPodcastInfoUseCase,
            audioPlayerController = audioPlayerController,
            clearHistoryUseCase = clearHistoryUseCase,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @Test
    fun `viewModel initializes successfully`() = runTest {
        // Just verify the viewModel can be created without errors and has expected initial state
        viewModel.uiState.test {
            val state = awaitItem()
            // The initial state might be loading=true, so just check that it's properly initialized
            assertFalse("Dialog should not be shown initially", state.showConfirmClearDialog)
        }
    }

    @Test
    fun `onAction ClearHistory shows confirmation dialog`() = runTest {
        viewModel.uiState.test {
            // First emit the initial state
            val initialState = awaitItem()
            assertFalse("Dialog should not be shown initially", initialState.showConfirmClearDialog)

            // When: clear history is requested
            viewModel.onAction(HistoryAction.ClearHistory)

            // Then: confirmation dialog should be shown in the next emission
            val updatedState = awaitItem()
            assertTrue("Confirm dialog should be shown", updatedState.showConfirmClearDialog)
        }
    }

    @Test
    fun `uiState exposes grouped history rows`() = runTest {
        val today = episodeWithPodcastInfo("today", datePlayedMs = daysAgo(0))
        val yesterday = episodeWithPodcastInfo("yesterday", datePlayedMs = daysAgo(1))
        every { getPlaybackHistoryWithPodcastInfoUseCase() } returns flowOf(listOf(yesterday, today))

        viewModel = HistoryViewModel(
            getPlaybackHistoryWithPodcastInfoUseCase = getPlaybackHistoryWithPodcastInfoUseCase,
            audioPlayerController = audioPlayerController,
            clearHistoryUseCase = clearHistoryUseCase,
            userPreferencesRepository = userPreferencesRepository
        )

        viewModel.uiState.test {
            val firstState = awaitItem()
            val loadedState = if (firstState.isLoading) awaitItem() else firstState

            assertEquals(listOf("today", "yesterday"), loadedState.historyItems.map { it.id })
            assertEquals(
                listOf(
                    HistoryListRow.SectionHeader(DateBucket.TODAY),
                    HistoryListRow.EpisodeRow(loadedState.historyItems[0]),
                    HistoryListRow.SectionHeader(DateBucket.YESTERDAY),
                    HistoryListRow.EpisodeRow(loadedState.historyItems[1])
                ),
                loadedState.historyRows
            )
        }
    }

    @Test
    fun `uiState keeps section headers before episodes in one handed mode`() = runTest {
        val today = episodeWithPodcastInfo("today", datePlayedMs = daysAgo(0))
        val yesterday = episodeWithPodcastInfo("yesterday", datePlayedMs = daysAgo(1))
        every { getPlaybackHistoryWithPodcastInfoUseCase() } returns flowOf(listOf(yesterday, today))
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(userSettings(oneHandedMode = true))

        viewModel = HistoryViewModel(
            getPlaybackHistoryWithPodcastInfoUseCase = getPlaybackHistoryWithPodcastInfoUseCase,
            audioPlayerController = audioPlayerController,
            clearHistoryUseCase = clearHistoryUseCase,
            userPreferencesRepository = userPreferencesRepository
        )

        viewModel.uiState.test {
            val firstState = awaitItem()
            val loadedState = if (firstState.isLoading) awaitItem() else firstState

            assertTrue(loadedState.oneHandedMode)
            assertEquals(listOf("today", "yesterday"), loadedState.historyItems.map { it.id })
            assertEquals(
                listOf(
                    HistoryListRow.SectionHeader(DateBucket.YESTERDAY),
                    HistoryListRow.EpisodeRow(loadedState.historyItems[1]),
                    HistoryListRow.SectionHeader(DateBucket.TODAY),
                    HistoryListRow.EpisodeRow(loadedState.historyItems[0])
                ),
                loadedState.historyRows
            )
        }
    }

    @Test
    fun `onAction ConfirmClearHistory calls use case`() = runTest {
        // When: clear is confirmed
        viewModel.onAction(HistoryAction.ConfirmClearHistory)
        advanceUntilIdle()

        // Then: use case should be called
        coVerify { clearHistoryUseCase() }
    }

    @Test
    fun `onAction OnEpisodeClick calls audio player`() = runTest {
        // Given: audio player is mocked
        coEvery { audioPlayerController.play(any()) } returns Unit

        // When: episode is clicked
        viewModel.onAction(HistoryAction.OnEpisodeClick("test-episode-guid"))
        advanceUntilIdle()

        // Then: audio player should be called
        coVerify { audioPlayerController.play("test-episode-guid") }
    }

    @Test
    fun `onAction DismissClearHistoryDialog hides dialog`() = runTest {
        viewModel.uiState.test {
            // First emit the initial state
            val initialState = awaitItem()
            assertFalse("Dialog should not be shown initially", initialState.showConfirmClearDialog)

            // First show the dialog
            viewModel.onAction(HistoryAction.ClearHistory)
            val showDialogState = awaitItem()
            assertTrue("Dialog should be shown", showDialogState.showConfirmClearDialog)

            // When: dismiss the dialog
            viewModel.onAction(HistoryAction.DismissClearHistoryDialog)

            // Then: dialog should be hidden
            val dismissedState = awaitItem()
            assertFalse("Dialog should be hidden", dismissedState.showConfirmClearDialog)
        }
    }

    private fun daysAgo(daysAgo: Long): Long =
        LocalDate.now(ZoneId.systemDefault())
            .minusDays(daysAgo)
            .atTime(10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun userSettings(oneHandedMode: Boolean): UserSettings =
        UserSettings(
            layoutMode = LayoutMode.LIST,
            gridSize = 2,
            showGridTitles = true,
            oneHandedMode = oneHandedMode,
            navBarHeight = 80,
            progressBarHeight = 4
        )

    private fun episodeWithPodcastInfo(
        guid: String,
        datePlayedMs: Long?
    ): EpisodeWithPodcastInfo =
        EpisodeWithPodcastInfo(
            episode =
            EpisodePresentation(
                guid = guid,
                title = "Episode $guid",
                link = null,
                description = "",
                podcastRssUrl = "https://example.com/feed.xml",
                isFavorite = false,
                isPlayed = true,
                playbackPositionMs = 0L,
                durationMs = 0L,
                pubDateMs = null,
                datePlayedMs = datePlayedMs,
                favoriteAddedAtMs = null,
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
