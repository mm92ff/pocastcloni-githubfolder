package com.example.pocastcloni.ui.home.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
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
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

// Internal state representation (clean & type-safe)
private data class PodcastContentState(
    val podcasts: ImmutableList<Podcast>,
    val contentLoad: RetainedLoad<ImmutableList<Podcast>>
)

private data class HomeConfigurationState(
    val settings: UserSettings,
    val editState: EditState,
    val showDeleteConfirmation: Boolean
)

private data class HomeActivityState(
    val isRefreshing: Boolean,
    val isPlayerVisible: Boolean,
    val screenError: UiText?
)

private data class IntermediateHomeState(
    val content: PodcastContentState,
    val configuration: HomeConfigurationState,
    val activity: HomeActivityState
)

private data class EditState(
    val isEditMode: Boolean = false,
    val selectedPodcastRssUrls: ImmutableSet<String> = persistentSetOf()
)

private data class ReorderRequest(
    val id: Long,
    val rssUrlsInOrder: List<String>,
    val preWriteDatabaseSequence: Long
)

private data class OptimisticOrder(
    val id: Long,
    val rssUrlsInOrder: List<String>,
    val preWriteDatabaseSequence: Long,
    val matchingDatabaseConfirmationSequence: Long? = null,
    val awaitingDatabaseConfirmation: Boolean = false
)

private data class DatabasePodcastOrder(
    val sequence: Long,
    val rssUrlsInOrder: List<String>
)

// One-time events for the UI (e.g. Snackbars)
sealed interface HomeUiEvent {
    data class ShowUserMessage(val message: UiText) : HomeUiEvent
}

@HiltViewModel
class HomeViewModel
@Inject
@Suppress("LongParameterList")
constructor(
    private val getAllPodcasts: GetAllPodcastsUseCase,
    private val getUserSettings: GetUserSettingsUseCase,
    private val refreshPodcasts: RefreshPodcastsUseCase,
    private val reorderPodcasts: ReorderPodcastsUseCase,
    private val markAllPodcastsSeen: MarkAllPodcastsSeenUseCase,
    private val deletePodcastUseCase: DeletePodcastUseCase,
    playerStatePort: PlayerStatePort,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val didRunStartRefresh = AtomicBoolean(false)
    private val ownsManualRefreshPresentation = AtomicBoolean(false)
    private val reorderRequestIds = AtomicLong(0L)
    private val databaseOrderSequences = AtomicLong(0L)
    private val latestDatabaseOrder = AtomicReference<DatabasePodcastOrder?>(null)
    private var lastStartRefreshAtMs: Long = 0L

    // Exposed "silent refresh" indicator for the TopBar
    private val _isAutoRefreshing = MutableStateFlow(false)
    val isAutoRefreshing: StateFlow<Boolean> = _isAutoRefreshing.asStateFlow()

    // Internal Mutable States
    private val _editState = MutableStateFlow(EditState())
    private val _isRefreshing = MutableStateFlow(false)

    private val _showDeleteConfirmation = MutableStateFlow(false)

    private val _screenError = MutableStateFlow<UiText?>(null)

    // Rebuilds optimistic display entries from current DB models instead of caching stale models.
    private val _optimisticOrder = MutableStateFlow<OptimisticOrder?>(null)

    /**
     * Receives identified, immutable RSS URL order snapshots from drag moves. Conflation keeps the
     * request already being persisted and only the latest identified request queued behind it, so
     * writes never overlap and superseded intermediate drag positions do not reach the database.
     */
    private val reorderRequests = Channel<ReorderRequest>(capacity = Channel.CONFLATED)

    // Event channel for one-shot UI actions (Snackbars)
    private val _events = Channel<HomeUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Player state flow uses the database episode ID.
    private val isPlayerVisibleFlow =
        playerStatePort.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()

    private val podcastsFlow =
        getAllPodcasts()
            .map { it.toImmutableList() }
            .onEach(::recordDatabaseOrderEmission)
            .distinctUntilChanged()
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    private val podcastContentFlow: Flow<PodcastContentState> =
        combine(
            podcastsFlow,
            _optimisticOrder
        ) { podcastLoad, optimisticOrder ->
            val dbPodcasts = podcastLoad.lastValue ?: emptyList<Podcast>().toImmutableList()
            val podcasts = dbPodcasts.inOrder(optimisticOrder?.rssUrlsInOrder)

            PodcastContentState(
                podcasts = podcasts,
                contentLoad =
                if (podcastLoad.lastValue == null) {
                    podcastLoad
                } else {
                    podcastLoad.copy(lastValue = podcasts)
                }
            )
        }

    private val homeConfigurationFlow: Flow<HomeConfigurationState> =
        combine(
            getUserSettings(),
            _editState,
            _showDeleteConfirmation
        ) { settings, editState, showDeleteConfirmation ->
            HomeConfigurationState(
                settings = settings,
                editState = editState,
                showDeleteConfirmation = showDeleteConfirmation
            )
        }

    private val homeActivityFlow: Flow<HomeActivityState> =
        combine(
            _isRefreshing,
            isPlayerVisibleFlow,
            _screenError
        ) { isRefreshing, isPlayerVisible, screenError ->
            HomeActivityState(
                isRefreshing = isRefreshing,
                isPlayerVisible = isPlayerVisible,
                screenError = screenError
            )
        }

    /** Combines named, typed state groups so each source has a compile-time checked destination. */
    private val intermediateStateFlow: Flow<IntermediateHomeState> =
        combine(
            podcastContentFlow,
            homeConfigurationFlow,
            homeActivityFlow
        ) { content, configuration, activity ->
            IntermediateHomeState(content, configuration, activity)
        }

    val uiState: StateFlow<HomeUiState> =
        intermediateStateFlow
            .map { state ->
                val content = state.content
                val configuration = state.configuration
                val activity = state.activity
                val selectedPodcasts =
                    if (configuration.editState.selectedPodcastRssUrls.isNotEmpty()) {
                        content.podcasts.filter {
                            it.rssUrl in configuration.editState.selectedPodcastRssUrls
                        }
                    } else {
                        emptyList()
                    }

                HomeUiState(
                    contentLoad = content.contentLoad,
                    podcasts = content.podcasts,
                    isLoading = content.contentLoad.loading,
                    layoutMode = configuration.settings.layoutMode,
                    gridSize = configuration.settings.gridSize,
                    showGridTitles = configuration.settings.showGridTitles,
                    transparentPodcastCards = configuration.settings.transparentPodcastCards,
                    oneHandedMode = configuration.settings.oneHandedMode,
                    isEditMode = configuration.editState.isEditMode,
                    selectedPodcastRssUrls = configuration.editState.selectedPodcastRssUrls,
                    confirmDelete = configuration.settings.confirmDelete,
                    indicatorColorArgb = configuration.settings.indicator.colorArgb,
                    indicatorSize = configuration.settings.indicator.size,
                    indicatorBorderWidth = configuration.settings.indicator.borderWidth,
                    indicatorXOffset = configuration.settings.indicator.xOffset,
                    indicatorYOffset = configuration.settings.indicator.yOffset,
                    isRefreshing = activity.isRefreshing,
                    progressBarHeight = configuration.settings.progressBarHeight,
                    navBarHeight = configuration.settings.navBarHeight,
                    homeBottomSpacing = configuration.settings.homeBottomSpacing,
                    isPlayerVisible = activity.isPlayerVisible,
                    userMessage = null,
                    showDeleteConfirmation = configuration.showDeleteConfirmation,
                    selectedPodcastsForDelete = selectedPodcasts,
                    screenError = activity.screenError
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
                initialValue = HomeUiState(isLoading = true)
            )

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            consumeReorderRequests()
        }
    }

    fun markAllAsSeen() {
        viewModelScope.launch(dispatcherProvider.io) {
            markAllPodcastsSeen()
        }
    }

    fun refresh() {
        val presentsResult = ownsManualRefreshPresentation.compareAndSet(false, true)
        viewModelScope.launch(dispatcherProvider.io) {
            if (presentsResult) {
                _isAutoRefreshing.value = false
                _isRefreshing.value = true
                _screenError.value = null
            }
            try {
                val summary = refreshPodcasts(forceFull = false)
                if (presentsResult) presentManualRefreshResult(summary)
            } catch (e: CancellationException) {
                throw e // structured concurrency requires this
            } catch (e: Exception) {
                if (presentsResult) presentManualRefreshError()
            } finally {
                if (presentsResult) {
                    _isRefreshing.value = false
                    ownsManualRefreshPresentation.set(false)
                }
            }
        }
    }

    fun autoRefreshOnStartIfEnabled() {
        if (!didRunStartRefresh.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        if (now - lastStartRefreshAtMs < 10 * 60 * 1000L) return
        lastStartRefreshAtMs = now

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val settings = getUserSettings().first()
                if (!settings.autoRefreshOnStart) return@launch

                val showIndicator = !ownsManualRefreshPresentation.get()
                if (showIndicator) _isAutoRefreshing.value = true
                try {
                    val summary = refreshPodcasts(forceFull = false)
                    if (summary.allFailed) {
                        Timber.w("Auto refresh on start failed for all %d podcasts", summary.totalCount)
                    } else if (summary.hasFailures) {
                        Timber.w(
                            "Auto refresh on start partially failed: %d succeeded, %d failed",
                            summary.successfulCount,
                            summary.failureCount
                        )
                    }
                } catch (e: CancellationException) {
                    throw e // structured concurrency requires this
                } catch (e: Exception) {
                    Timber.w(e, "Auto refresh on start failed")
                } finally {
                    if (showIndicator) _isAutoRefreshing.value = false
                }
            } catch (e: CancellationException) {
                throw e // structured concurrency requires this
            } catch (e: Exception) {
                _isAutoRefreshing.value = false
                Timber.w(e, "Auto refresh on start failed (unexpected)")
            }
        }
    }

    private suspend fun presentManualRefreshResult(summary: PodcastUpdateSummary) {
        val errorText = UiText.StringResource(R.string.refresh_error)
        if (summary.allFailed && uiState.value.podcasts.isEmpty()) {
            _screenError.value = errorText
        } else if (summary.allFailed) {
            _events.send(HomeUiEvent.ShowUserMessage(errorText))
        } else if (summary.hasFailures) {
            _events.send(
                HomeUiEvent.ShowUserMessage(
                    UiText.StringResource(
                        R.string.podcasts_partially_updated,
                        summary.successfulCount,
                        summary.totalCount
                    )
                )
            )
        }
    }

    private suspend fun presentManualRefreshError() {
        val errorText = UiText.StringResource(R.string.refresh_error)
        if (uiState.value.podcasts.isEmpty()) {
            _screenError.value = errorText
        } else {
            _events.send(HomeUiEvent.ShowUserMessage(errorText))
        }
    }

    fun enterEditMode(initialPodcastUrl: String) {
        _editState.update {
            it.copy(
                isEditMode = true,
                selectedPodcastRssUrls = persistentSetOf(initialPodcastUrl)
            )
        }
    }

    fun exitEditMode() {
        _editState.update { it.copy(isEditMode = false, selectedPodcastRssUrls = persistentSetOf()) }
    }

    @Suppress("ReturnCount")
    fun onReorder(
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return

        val currentPodcasts = uiState.value.podcasts
        val currentRssUrls =
            _optimisticOrder.value
                ?.rssUrlsInOrder
                ?.takeIf { it.isCompleteOrderOf(currentPodcasts) }
                ?: currentPodcasts.map(Podcast::rssUrl)
        if (fromIndex !in currentRssUrls.indices || toIndex !in currentRssUrls.indices) return

        val reorderedRssUrls = currentRssUrls.toMutableList()
        val movedRssUrl = reorderedRssUrls.removeAt(fromIndex)
        reorderedRssUrls.add(toIndex, movedRssUrl)
        if (reorderedRssUrls == currentRssUrls) return

        val immutableSnapshot = reorderedRssUrls.toList()
        val preWriteDatabaseSequence = latestDatabaseOrder.get()?.sequence ?: 0L
        val request =
            ReorderRequest(
                id = reorderRequestIds.incrementAndGet(),
                rssUrlsInOrder = immutableSnapshot,
                preWriteDatabaseSequence = preWriteDatabaseSequence
            )
        _optimisticOrder.value =
            OptimisticOrder(
                id = request.id,
                rssUrlsInOrder = request.rssUrlsInOrder,
                preWriteDatabaseSequence = request.preWriteDatabaseSequence
            )
        latestDatabaseOrder.get()?.let(::reconcileOptimisticOrderWithDatabase)
        if (_optimisticOrder.value?.id != request.id) return
        reorderRequests.trySend(request)
    }

    /**
     * Persists orders serially. Success marks only the matching request as awaiting a confirming DB
     * emission; failure clears only the matching request. Cancellation escapes immediately with
     * structured concurrency, while ordinary failures are reported before the latest request runs.
     */
    private suspend fun consumeReorderRequests() {
        for (request in reorderRequests) {
            try {
                reorderPodcasts(rssUrlsInOrder = request.rssUrlsInOrder)
                _optimisticOrder.update { current ->
                    if (current?.id == request.id) {
                        val wasConfirmedWhilePending =
                            current.matchingDatabaseConfirmationSequence?.let { sequence ->
                                sequence > request.preWriteDatabaseSequence
                            } == true
                        if (wasConfirmedWhilePending) {
                            null
                        } else {
                            current.copy(awaitingDatabaseConfirmation = true)
                        }
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _optimisticOrder.update { current ->
                    current?.takeUnless { it.id == request.id }
                }
                Timber.e(e, "Failed to persist podcast order")
                _events.send(HomeUiEvent.ShowUserMessage(UiText.StringResource(R.string.reorder_error)))
            }
        }
    }

    /**
     * Records every raw DB emission before downstream UI deduplication, then reconciles by request
     * identity and sequence. A matching emission newer than the request's pre-write sequence is
     * retained while pending or clears an awaiting order; membership changes always invalidate it.
     */
    private fun recordDatabaseOrderEmission(dbPodcasts: ImmutableList<Podcast>) {
        val databaseOrder =
            DatabasePodcastOrder(
                sequence = databaseOrderSequences.incrementAndGet(),
                rssUrlsInOrder = dbPodcasts.map(Podcast::rssUrl)
            )
        latestDatabaseOrder.set(databaseOrder)
        reconcileOptimisticOrderWithDatabase(databaseOrder)
    }

    @Suppress("ReturnCount")
    private fun reconcileOptimisticOrderWithDatabase(databaseOrder: DatabasePodcastOrder) {
        while (true) {
            val current = _optimisticOrder.value ?: return
            val membershipChanged =
                !current.rssUrlsInOrder.hasSameMembershipAs(databaseOrder.rssUrlsInOrder)
            if (membershipChanged) {
                if (_optimisticOrder.compareAndSet(current, null)) return
                continue
            }

            val isNewMatchingConfirmation =
                databaseOrder.sequence > current.preWriteDatabaseSequence &&
                    databaseOrder.rssUrlsInOrder == current.rssUrlsInOrder
            if (!isNewMatchingConfirmation) return

            val updated =
                if (current.awaitingDatabaseConfirmation) {
                    null
                } else if (
                    current.matchingDatabaseConfirmationSequence == null ||
                    databaseOrder.sequence > current.matchingDatabaseConfirmationSequence
                ) {
                    current.copy(matchingDatabaseConfirmationSequence = databaseOrder.sequence)
                } else {
                    return
                }
            if (_optimisticOrder.compareAndSet(current, updated)) return
        }
    }

    fun toggleSelection(podcastUrl: String) {
        _editState.update { state ->
            val current = state.selectedPodcastRssUrls
            // '+' and '-' create new ImmutableSets
            val newSet =
                if (current.contains(podcastUrl)) {
                    current - podcastUrl
                } else {
                    current + podcastUrl
                }
            state.copy(selectedPodcastRssUrls = newSet.toImmutableSet())
        }
    }

    fun onPodcastInteract(targetUrl: String) {
        if (!uiState.value.isEditMode) return
        toggleSelection(targetUrl)
    }

    fun onDeleteSelectedRequest() {
        val selectedCount = _editState.value.selectedPodcastRssUrls.size
        if (selectedCount == 0) return

        if (uiState.value.confirmDelete) {
            _showDeleteConfirmation.value = true
        } else {
            executeDeleteSelected()
        }
    }

    fun confirmDelete() {
        executeDeleteSelected()
        cancelDelete()
    }

    fun cancelDelete() {
        _showDeleteConfirmation.value = false
    }

    private fun executeDeleteSelected() {
        val rssUrlsToDelete = _editState.value.selectedPodcastRssUrls
        if (rssUrlsToDelete.isEmpty()) return

        val podcastsToDelete = uiState.value.podcasts.filter { it.rssUrl in rssUrlsToDelete }

        viewModelScope.launch(dispatcherProvider.io) {
            podcastsToDelete.forEach { podcast ->
                try {
                    deletePodcastUseCase(podcast)
                } catch (e: CancellationException) {
                    throw e // structured concurrency requires this
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete podcast ${podcast.title}")
                }
            }
            // Exit edit mode after successful deletion
            exitEditMode()
        }
    }

    fun clearScreenError() {
        _screenError.value = null
    }
}

private fun ImmutableList<Podcast>.inOrder(rssUrlsInOrder: List<String>?): ImmutableList<Podcast> {
    if (rssUrlsInOrder == null || !rssUrlsInOrder.isCompleteOrderOf(this)) return this

    val podcastsByRssUrl = associateBy(Podcast::rssUrl)
    return rssUrlsInOrder.map { rssUrl -> requireNotNull(podcastsByRssUrl[rssUrl]) }.toImmutableList()
}

private fun List<String>.isCompleteOrderOf(podcasts: List<Podcast>): Boolean {
    return hasSameMembershipAs(podcasts.map(Podcast::rssUrl))
}

private fun List<String>.hasSameMembershipAs(other: List<String>): Boolean {
    if (size != other.size) return false

    val membership = toSet()
    val otherMembership = other.toSet()
    return membership.size == size && otherMembership.size == other.size && membership == otherMembership
}
