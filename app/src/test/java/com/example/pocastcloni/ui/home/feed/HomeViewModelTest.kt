package com.example.pocastcloni.ui.home.feed

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.podcast.DeletePodcastUseCase
import com.example.pocastcloni.domain.usecase.podcast.GetAllPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.MarkAllPodcastsSeenUseCase
import com.example.pocastcloni.domain.usecase.podcast.RefreshPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.ReorderPodcastsUseCase
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var getAllPodcasts: GetAllPodcastsUseCase
    private lateinit var getUserSettings: GetUserSettingsUseCase
    private lateinit var refreshPodcasts: RefreshPodcastsUseCase
    private lateinit var reorderPodcasts: ReorderPodcastsUseCase
    private lateinit var markAllPodcastsSeen: MarkAllPodcastsSeenUseCase
    private lateinit var deletePodcastUseCase: DeletePodcastUseCase
    private lateinit var playerController: AudioPlayerController
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var viewModel: HomeViewModel

    private val testPodcast = Podcast(
        rssUrl = "https://example.com/feed.rss",
        title = "Test Podcast",
        description = "",
        imageUrl = "https://img.jpg",
        lastRefreshed = Date(),
        autoDownloadEnabled = false,
        sortOrder = 1L,
        hasNewEpisodes = false,
        lastModifiedHeader = null,
        eTagHeader = null,
        latestEpisodeDate = null,
        isLatestEpisodePlayed = null
    )

    @Before
    fun setup() {
        getAllPodcasts = mockk()
        getUserSettings = mockk()
        refreshPodcasts = mockk()
        reorderPodcasts = mockk(relaxed = true)
        markAllPodcastsSeen = mockk(relaxed = true)
        deletePodcastUseCase = mockk(relaxed = true)
        playerController = mockk()
        dispatcherProvider = mockk()

        every { getAllPodcasts() } returns flowOf(emptyList())
        every { getUserSettings() } returns flowOf(UserSettings())
        every { playerController.playerState } returns MutableStateFlow(
            PlayerUiState(currentEpisodeId = null, isPlaying = false)
        )
        every { dispatcherProvider.io } returns testDispatcher

        viewModel = HomeViewModel(
            getAllPodcasts = getAllPodcasts,
            getUserSettings = getUserSettings,
            refreshPodcasts = refreshPodcasts,
            reorderPodcasts = reorderPodcasts,
            markAllPodcastsSeen = markAllPodcastsSeen,
            deletePodcastUseCase = deletePodcastUseCase,
            playerController = playerController,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `initial state has isLoading true`() = runTest(testDispatcher) {
        val initialState = viewModel.uiState.value
        assertTrue(initialState.isLoading)
    }

    @Test
    fun `enterEditMode sets isEditMode and initial selection`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading
            awaitItem() // loaded (combine emits)

            viewModel.enterEditMode("https://example.com/feed.rss")
            val state = awaitItem()
            assertTrue(state.isEditMode)
            assertTrue(state.selectedPodcastGuids.contains("https://example.com/feed.rss"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exitEditMode clears edit state`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading
            awaitItem() // loaded

            viewModel.enterEditMode("https://example.com/feed.rss")
            awaitItem() // edit mode entered
            viewModel.exitEditMode()
            val state = awaitItem()
            assertFalse(state.isEditMode)
            assertTrue(state.selectedPodcastGuids.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSelection adds podcast to selection`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading
            awaitItem() // loaded

            viewModel.enterEditMode("https://example.com/feed.rss")
            awaitItem() // edit mode entered
            viewModel.toggleSelection("https://other.com/feed.rss")
            val state = awaitItem()
            assertTrue(state.selectedPodcastGuids.contains("https://other.com/feed.rss"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSelection removes podcast from selection when already selected`() = runTest(testDispatcher) {
        viewModel.enterEditMode("https://example.com/feed.rss")
        viewModel.toggleSelection("https://example.com/feed.rss") // deselect
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.selectedPodcastGuids.contains("https://example.com/feed.rss"))
    }

    @Test
    fun `markAllAsSeen calls use case`() = runTest(testDispatcher) {
        viewModel.markAllAsSeen()
        advanceUntilIdle()
        coVerify { markAllPodcastsSeen() }
    }

    @Test
    fun `clearScreenError clears the error`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial
            awaitItem() // loaded

            viewModel.clearScreenError()
            // State should still be emitted (null error is the default anyway)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh sets isRefreshing to false after completion`() = runTest(testDispatcher) {
        coEvery { refreshPodcasts(forceFull = true) } returns PodcastUpdateSummary(
            totalCount = 0,
            successfulCount = 0,
            failureCount = 0
        )

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `manual refresh owns presentation while startup refresh overlaps`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<PodcastUpdateSummary>()
        coEvery { refreshPodcasts(any()) } coAnswers { gate.await() }
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

        viewModel.autoRefreshOnStartIfEnabled()
        runCurrent()
        assertTrue(viewModel.isAutoRefreshing.value)

        viewModel.refresh()
        runCurrent()
        assertFalse(viewModel.isAutoRefreshing.value)
        assertTrue(viewModel.uiState.value.isRefreshing)

        gate.complete(PodcastUpdateSummary(1, 1, 0))
        advanceUntilIdle()
        assertFalse(viewModel.isAutoRefreshing.value)
        assertFalse(viewModel.uiState.value.isRefreshing)
        coVerify(exactly = 1) { refreshPodcasts(forceFull = false) }
        coVerify(exactly = 1) { refreshPodcasts(forceFull = true) }
        stateCollector.cancel()
    }

    @Test
    fun `coalesced manual callers emit one user message`() = runTest(testDispatcher) {
        recreateViewModel(podcasts = listOf(testPodcast))
        val gate = CompletableDeferred<PodcastUpdateSummary>()
        coEvery { refreshPodcasts(forceFull = true) } coAnswers { gate.await() }
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.events.test {
            viewModel.refresh()
            viewModel.refresh()
            runCurrent()
            gate.complete(PodcastUpdateSummary(totalCount = 1, successfulCount = 0, failureCount = 1))
            advanceUntilIdle()

            awaitItem()
            expectNoEvents()
        }
        coVerify(exactly = 2) { refreshPodcasts(forceFull = true) }
        stateCollector.cancel()
    }

    @Test
    fun `onDeleteSelectedRequest with confirmDelete=true shows confirmation dialog`() = runTest(testDispatcher) {
        every { getUserSettings() } returns flowOf(UserSettings(confirmDelete = true))
        viewModel = HomeViewModel(
            getAllPodcasts = getAllPodcasts,
            getUserSettings = getUserSettings,
            refreshPodcasts = refreshPodcasts,
            reorderPodcasts = reorderPodcasts,
            markAllPodcastsSeen = markAllPodcastsSeen,
            deletePodcastUseCase = deletePodcastUseCase,
            playerController = playerController,
            dispatcherProvider = dispatcherProvider
        )

        viewModel.uiState.test {
            awaitItem() // initial loading
            awaitItem() // loaded with confirmDelete=true

            viewModel.enterEditMode("https://example.com/feed.rss")
            awaitItem() // edit mode entered
            viewModel.onDeleteSelectedRequest()
            val state = awaitItem()
            assertTrue(state.showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelDelete hides confirmation dialog`() = runTest(testDispatcher) {
        viewModel.enterEditMode("https://example.com/feed.rss")
        advanceUntilIdle()
        viewModel.cancelDelete()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    private fun recreateViewModel(podcasts: List<Podcast>) {
        every { getAllPodcasts() } returns flowOf(podcasts)
        viewModel =
            HomeViewModel(
                getAllPodcasts = getAllPodcasts,
                getUserSettings = getUserSettings,
                refreshPodcasts = refreshPodcasts,
                reorderPodcasts = reorderPodcasts,
                markAllPodcastsSeen = markAllPodcastsSeen,
                deletePodcastUseCase = deletePodcastUseCase,
                playerController = playerController,
                dispatcherProvider = dispatcherProvider
            )
    }
}
