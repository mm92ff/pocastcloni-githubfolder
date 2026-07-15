package com.example.pocastcloni.ui.home.feed

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.podcast.DeletePodcastUseCase
import com.example.pocastcloni.domain.usecase.podcast.GetAllPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.MarkAllPodcastsSeenUseCase
import com.example.pocastcloni.domain.usecase.podcast.RefreshPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.ReorderPodcastsUseCase
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.playback.api.PlayerUiState
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
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
    private lateinit var playerStatePort: PlayerStatePort
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
        playerStatePort = mockk()
        dispatcherProvider = mockk()

        every { getAllPodcasts() } returns flowOf(emptyList())
        every { getUserSettings() } returns flowOf(UserSettings())
        every { playerStatePort.playerState } returns MutableStateFlow(
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
            playerStatePort = playerStatePort,
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
            assertTrue(state.selectedPodcastRssUrls.contains("https://example.com/feed.rss"))
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
            assertTrue(state.selectedPodcastRssUrls.isEmpty())
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
            assertTrue(state.selectedPodcastRssUrls.contains("https://other.com/feed.rss"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSelection removes podcast from selection when already selected`() = runTest(testDispatcher) {
        viewModel.enterEditMode("https://example.com/feed.rss")
        viewModel.toggleSelection("https://example.com/feed.rss") // deselect
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.selectedPodcastRssUrls.contains("https://example.com/feed.rss"))
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
    fun `rapid reorders keep one write active and persist the newest snapshot last`() =
        runTest(testDispatcher) {
            val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2), podcast("d", 3))
            val podcastsFlow = MutableStateFlow(podcasts)
            val podcastsByRssUrl = podcasts.associateBy(Podcast::rssUrl)
            val firstWriteRelease = CompletableDeferred<Unit>()
            val startedOrders = mutableListOf<List<String>>()
            val completedOrders = mutableListOf<List<String>>()
            var activeWrites = 0
            var maxActiveWrites = 0
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
                val order = firstArg<List<String>>().toList()
                startedOrders += order
                activeWrites += 1
                maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
                try {
                    if (startedOrders.size == 1) firstWriteRelease.await()
                    completedOrders += order
                } finally {
                    activeWrites -= 1
                }
            }
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings(oneHandedMode = true)),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            viewModel.onReorder(fromIndex = 1, toIndex = 2)
            viewModel.onReorder(fromIndex = 2, toIndex = 3)
            runCurrent()

            val firstOrder = listOf(podcasts[1], podcasts[0], podcasts[2], podcasts[3]).map(Podcast::rssUrl)
            val newestOrder = listOf(podcasts[1], podcasts[2], podcasts[3], podcasts[0]).map(Podcast::rssUrl)
            assertTrue(viewModel.uiState.value.oneHandedMode)
            assertEquals(listOf(firstOrder), startedOrders)
            assertEquals(1, maxActiveWrites)

            firstWriteRelease.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(firstOrder, newestOrder), startedOrders)
            assertEquals(listOf(firstOrder, newestOrder), completedOrders)
            assertEquals(newestOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))
            assertEquals(1, maxActiveWrites)

            podcastsFlow.value = newestOrder.map { rssUrl -> requireNotNull(podcastsByRssUrl[rssUrl]) }
            runCurrent()
            assertEquals(newestOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))
            stateCollector.cancel()
        }

    @Test
    fun `write completion awaits matching database emission before clearing overlay`() =
        runTest(testDispatcher) {
            val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2))
            val podcastsFlow = MutableStateFlow(podcasts)
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } returns Unit
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings()),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            advanceUntilIdle()

            val persistedOrder = listOf(podcasts[1], podcasts[0], podcasts[2])
            assertEquals(podcasts, podcastsFlow.value)
            assertEquals(persistedOrder.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            podcastsFlow.value = persistedOrder
            runCurrent()
            assertEquals(persistedOrder.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            val laterDatabaseOrder = listOf(podcasts[2], podcasts[0], podcasts[1])
            podcastsFlow.value = laterDatabaseOrder
            runCurrent()

            assertEquals(
                laterDatabaseOrder.map(Podcast::rssUrl),
                viewModel.uiState.value.podcasts.map(Podcast::rssUrl)
            )
            stateCollector.cancel()
        }

    @Test
    fun `matching database emission before write return confirms after success`() =
        runTest(testDispatcher) {
            val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2))
            val persistedOrder = listOf(podcasts[1], podcasts[0], podcasts[2])
            val laterDatabaseOrder = listOf(podcasts[2], podcasts[0], podcasts[1])
            val podcastsFlow = MutableStateFlow(podcasts)
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
                podcastsFlow.value = persistedOrder
                yield()
            }
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings()),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            advanceUntilIdle()

            assertEquals(persistedOrder, podcastsFlow.value)
            assertEquals(persistedOrder.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            podcastsFlow.value = laterDatabaseOrder
            runCurrent()
            assertEquals(
                laterDatabaseOrder.map(Podcast::rssUrl),
                viewModel.uiState.value.podcasts.map(Podcast::rssUrl)
            )
            stateCollector.cancel()
        }

    @Test
    fun `identical raw database emission confirms latest request restoring original order`() =
        runTest(testDispatcher) {
            val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2))
            val laterDatabaseOrder = listOf(podcasts[2], podcasts[0], podcasts[1])
            val podcastsFlow = MutableSharedFlow<List<Podcast>>(replay = 1)
            podcastsFlow.emit(podcasts)
            val firstWriteRelease = CompletableDeferred<Unit>()
            var writeCount = 0
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
                writeCount += 1
                if (writeCount == 1) {
                    firstWriteRelease.await()
                    error("older write failed")
                }
                assertEquals(podcasts.map(Podcast::rssUrl), firstArg<List<String>>())
                podcastsFlow.emit(podcasts)
                yield()
            }
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings()),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            viewModel.onReorder(fromIndex = 1, toIndex = 0)
            runCurrent()

            firstWriteRelease.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, writeCount)
            assertEquals(podcasts.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            podcastsFlow.emit(laterDatabaseOrder)
            runCurrent()
            assertEquals(
                laterDatabaseOrder.map(Podcast::rssUrl),
                viewModel.uiState.value.podcasts.map(Podcast::rssUrl)
            )
            stateCollector.cancel()
        }

    @Test
    fun `invalid and unchanged reorders do not write`() = runTest(testDispatcher) {
        recreateViewModel(podcasts = listOf(podcast("a", 0), podcast("b", 1)))
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.onReorder(fromIndex = 0, toIndex = 0)
        viewModel.onReorder(fromIndex = -1, toIndex = 0)
        viewModel.onReorder(fromIndex = 0, toIndex = 2)
        advanceUntilIdle()

        coVerify(exactly = 0) { reorderPodcasts(rssUrlsInOrder = any()) }
        stateCollector.cancel()
    }

    @Test
    fun `older failure preserves newer request with the same order`() = runTest(testDispatcher) {
        val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2))
        val podcastsFlow = MutableStateFlow(podcasts)
        val firstWriteRelease = CompletableDeferred<Unit>()
        val secondWriteRelease = CompletableDeferred<Unit>()
        val secondWriteStarted = CompletableDeferred<Unit>()
        val startedOrders = mutableListOf<List<String>>()
        coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
            val order = firstArg<List<String>>().toList()
            startedOrders += order
            if (startedOrders.size == 1) {
                firstWriteRelease.await()
                error("write failed")
            }
            secondWriteStarted.complete(Unit)
            secondWriteRelease.await()
        }
        recreateViewModel(
            podcastsFlow = podcastsFlow,
            settingsFlow = flowOf(UserSettings()),
            playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
        )
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.events.test {
            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            viewModel.onReorder(fromIndex = 1, toIndex = 0)
            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()

            val firstOrder = listOf(podcasts[1], podcasts[0], podcasts[2]).map(Podcast::rssUrl)
            assertEquals(firstOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            firstWriteRelease.complete(Unit)
            runCurrent()
            assertTrue(awaitItem() is HomeUiEvent.ShowUserMessage)
            assertTrue(secondWriteStarted.isCompleted)
            assertEquals(listOf(firstOrder, firstOrder), startedOrders)
            assertEquals(firstOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            secondWriteRelease.complete(Unit)
            advanceUntilIdle()

            assertEquals(firstOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))
            podcastsFlow.value = listOf(podcasts[1], podcasts[0], podcasts[2])
            runCurrent()
            assertEquals(firstOrder, viewModel.uiState.value.podcasts.map(Podcast::rssUrl))
            cancelAndIgnoreRemainingEvents()
        }
        stateCollector.cancel()
    }

    @Test
    fun `exit during in-flight reorder retains display until database confirmation`() =
        runTest(testDispatcher) {
            val podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2))
            val podcastsFlow = MutableStateFlow(podcasts)
            val writeRelease = CompletableDeferred<Unit>()
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
                writeRelease.await()
            }
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings()),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.enterEditMode(podcasts.first().rssUrl)
            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            val reordered = listOf(podcasts[1], podcasts[0], podcasts[2])
            assertEquals(reordered.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            viewModel.exitEditMode()
            runCurrent()
            assertFalse(viewModel.uiState.value.isEditMode)
            assertTrue(viewModel.uiState.value.selectedPodcastRssUrls.isEmpty())
            assertEquals(reordered.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            writeRelease.complete(Unit)
            advanceUntilIdle()
            assertEquals(reordered.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))

            podcastsFlow.value = reordered
            runCurrent()
            assertEquals(reordered.map(Podcast::rssUrl), viewModel.uiState.value.podcasts.map(Podcast::rssUrl))
            stateCollector.cancel()
        }

    @Test
    fun `reorder consumer rethrows cancellation without emitting an error`() = runTest(testDispatcher) {
        val writeStarted = CompletableDeferred<Unit>()
        val writeCancelled = CompletableDeferred<Unit>()
        coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers {
            writeStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                writeCancelled.complete(Unit)
            }
        }
        recreateViewModel(podcasts = listOf(podcast("a", 0), podcast("b", 1), podcast("c", 2)))
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.events.test {
            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            assertTrue(writeStarted.isCompleted)

            viewModel.viewModelScope.cancel()
            runCurrent()
            assertTrue(writeCancelled.isCompleted)
            expectNoEvents()

            viewModel.onReorder(fromIndex = 1, toIndex = 2)
            runCurrent()
            coVerify(exactly = 1) { reorderPodcasts(rssUrlsInOrder = any()) }
            cancelAndIgnoreRemainingEvents()
        }
        stateCollector.cancel()
    }

    @Test
    fun `optimistic order rebuilds from current models and rejects changed membership`() =
        runTest(testDispatcher) {
            val podcastA = podcast("a", 0)
            val podcastB = podcast("b", 1)
            val podcastC = podcast("c", 2)
            val podcastsFlow = MutableStateFlow(listOf(podcastA, podcastB))
            val writeRelease = CompletableDeferred<Unit>()
            coEvery { reorderPodcasts(rssUrlsInOrder = any()) } coAnswers { writeRelease.await() }
            recreateViewModel(
                podcastsFlow = podcastsFlow,
                settingsFlow = flowOf(UserSettings()),
                playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
            )
            val stateCollector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            runCurrent()

            viewModel.onReorder(fromIndex = 0, toIndex = 1)
            runCurrent()
            podcastsFlow.value =
                listOf(
                    podcastA.copy(title = "A updated"),
                    podcastB.copy(title = "B updated")
                )
            runCurrent()

            assertEquals(listOf("B updated", "A updated"), viewModel.uiState.value.podcasts.map(Podcast::title))

            podcastsFlow.value = listOf(podcastA.copy(title = "A newest"), podcastC)
            runCurrent()

            assertEquals(
                listOf(podcastA.rssUrl, podcastC.rssUrl),
                viewModel.uiState.value.podcasts.map(Podcast::rssUrl)
            )

            podcastsFlow.value = listOf(podcastA, podcastB)
            runCurrent()
            assertEquals(
                listOf(podcastA.rssUrl, podcastB.rssUrl),
                viewModel.uiState.value.podcasts.map(Podcast::rssUrl)
            )

            writeRelease.complete(Unit)
            advanceUntilIdle()
            stateCollector.cancel()
        }

    @Suppress("LongMethod")
    @Test
    fun `typed state sources map and update in isolation`() = runTest(testDispatcher) {
        val podcastsFlow = MutableStateFlow(listOf(testPodcast))
        val settingsFlow =
            MutableStateFlow(
                UserSettings(
                    layoutMode = LayoutMode.LIST,
                    gridSize = 173,
                    showGridTitles = false,
                    confirmDelete = true,
                    progressBarHeight = 11,
                    navBarHeight = 17,
                    oneHandedMode = true,
                    transparentPodcastCards = true,
                    indicator = IndicatorSettings(42L, 7, 3, 5, 9)
                )
            )
        val playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = 42L, isPlaying = true))
        recreateViewModel(podcastsFlow, settingsFlow, playerState)
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.enterEditMode(testPodcast.rssUrl)
        runCurrent()
        viewModel.onDeleteSelectedRequest()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(testPodcast), state.podcasts)
        assertEquals(LayoutMode.LIST, state.layoutMode)
        assertEquals(173, state.gridSize)
        assertFalse(state.showGridTitles)
        assertTrue(state.transparentPodcastCards)
        assertTrue(state.oneHandedMode)
        assertTrue(state.confirmDelete)
        assertEquals(42L, state.indicatorColorArgb)
        assertEquals(7, state.indicatorSize)
        assertEquals(3, state.indicatorBorderWidth)
        assertEquals(5, state.indicatorXOffset)
        assertEquals(9, state.indicatorYOffset)
        assertEquals(11, state.progressBarHeight)
        assertEquals(17, state.navBarHeight)
        assertTrue(state.isPlayerVisible)
        assertTrue(state.isEditMode)
        assertEquals(setOf(testPodcast.rssUrl), state.selectedPodcastRssUrls)
        assertTrue(state.showDeleteConfirmation)
        assertEquals(listOf(testPodcast), state.selectedPodcastsForDelete)

        val updatedPodcast = testPodcast.copy(title = "Updated title")
        podcastsFlow.value = listOf(updatedPodcast)
        runCurrent()
        val afterPodcastUpdate = viewModel.uiState.value
        assertEquals(listOf(updatedPodcast), afterPodcastUpdate.podcasts)
        assertEquals(173, afterPodcastUpdate.gridSize)
        assertTrue(afterPodcastUpdate.isPlayerVisible)

        settingsFlow.value = settingsFlow.value.copy(layoutMode = LayoutMode.GRID, gridSize = 191)
        runCurrent()
        val afterSettingsUpdate = viewModel.uiState.value
        assertEquals(listOf(updatedPodcast), afterSettingsUpdate.podcasts)
        assertEquals(LayoutMode.GRID, afterSettingsUpdate.layoutMode)
        assertEquals(191, afterSettingsUpdate.gridSize)
        assertTrue(afterSettingsUpdate.isPlayerVisible)

        playerState.value = PlayerUiState(currentEpisodeId = null, isPlaying = false)
        runCurrent()
        val afterPlayerUpdate = viewModel.uiState.value
        assertEquals(listOf(updatedPodcast), afterPlayerUpdate.podcasts)
        assertEquals(191, afterPlayerUpdate.gridSize)
        assertFalse(afterPlayerUpdate.isPlayerVisible)
        assertEquals(setOf(testPodcast.rssUrl), afterPlayerUpdate.selectedPodcastRssUrls)
        stateCollector.cancel()
    }

    @Test
    fun `internal state sources update only their owned home fields`() = runTest(testDispatcher) {
        val settings = UserSettings(confirmDelete = true, gridSize = 177)
        val refreshResult = CompletableDeferred<PodcastUpdateSummary>()
        coEvery { refreshPodcasts(forceFull = true) } coAnswers { refreshResult.await() }
        recreateViewModel(podcasts = emptyList(), settings = settings)
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
        runCurrent()

        viewModel.enterEditMode(testPodcast.rssUrl)
        runCurrent()
        val afterEdit = viewModel.uiState.value
        assertTrue(afterEdit.isEditMode)
        assertEquals(setOf(testPodcast.rssUrl), afterEdit.selectedPodcastRssUrls)
        assertEquals(177, afterEdit.gridSize)
        assertFalse(afterEdit.isRefreshing)

        viewModel.onDeleteSelectedRequest()
        runCurrent()
        val afterDeleteRequest = viewModel.uiState.value
        assertTrue(afterDeleteRequest.showDeleteConfirmation)
        assertTrue(afterDeleteRequest.isEditMode)
        assertEquals(177, afterDeleteRequest.gridSize)

        viewModel.cancelDelete()
        viewModel.refresh()
        runCurrent()
        val duringRefresh = viewModel.uiState.value
        assertFalse(duringRefresh.showDeleteConfirmation)
        assertTrue(duringRefresh.isRefreshing)
        assertTrue(duringRefresh.isEditMode)
        assertEquals(177, duringRefresh.gridSize)
        assertEquals(null, duringRefresh.screenError)

        refreshResult.complete(PodcastUpdateSummary(totalCount = 1, successfulCount = 0, failureCount = 1))
        advanceUntilIdle()
        val afterRefreshFailure = viewModel.uiState.value
        assertFalse(afterRefreshFailure.isRefreshing)
        assertTrue(afterRefreshFailure.screenError != null)
        assertTrue(afterRefreshFailure.isEditMode)
        assertEquals(177, afterRefreshFailure.gridSize)

        viewModel.clearScreenError()
        runCurrent()
        val afterErrorClear = viewModel.uiState.value
        assertEquals(null, afterErrorClear.screenError)
        assertTrue(afterErrorClear.isEditMode)
        assertEquals(177, afterErrorClear.gridSize)
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
            playerStatePort = playerStatePort,
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

    private fun podcast(
        id: String,
        sortOrder: Long
    ): Podcast =
        testPodcast.copy(
            rssUrl = "https://example.com/$id.rss",
            title = id.uppercase(),
            sortOrder = sortOrder
        )

    private fun recreateViewModel(
        podcasts: List<Podcast>,
        settings: UserSettings = UserSettings()
    ) {
        recreateViewModel(
            podcastsFlow = flowOf(podcasts),
            settingsFlow = flowOf(settings),
            playerState = MutableStateFlow(PlayerUiState(currentEpisodeId = null, isPlaying = false))
        )
    }

    private fun recreateViewModel(
        podcastsFlow: Flow<List<Podcast>>,
        settingsFlow: Flow<UserSettings>,
        playerState: StateFlow<PlayerUiState>
    ) {
        every { getAllPodcasts() } returns podcastsFlow
        every { getUserSettings() } returns settingsFlow
        every { playerStatePort.playerState } returns playerState
        viewModel =
            HomeViewModel(
                getAllPodcasts = getAllPodcasts,
                getUserSettings = getUserSettings,
                refreshPodcasts = refreshPodcasts,
                reorderPodcasts = reorderPodcasts,
                markAllPodcastsSeen = markAllPodcastsSeen,
                deletePodcastUseCase = deletePodcastUseCase,
                playerStatePort = playerStatePort,
                dispatcherProvider = dispatcherProvider
            )
    }
}
