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
import com.example.pocastcloni.ui.player.AudioPlayerController
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

// Internal state representation (clean & type-safe)
private data class IntermediateHomeState(
    val podcasts: ImmutableList<Podcast>,
    val contentLoad: RetainedLoad<ImmutableList<Podcast>>,
    val settings: UserSettings,
    val editState: EditState,
    val isRefreshing: Boolean,
    val isPlayerVisible: Boolean,
    val showDeleteConfirmation: Boolean,
    val screenError: UiText?
)

private data class EditState(
    val isEditMode: Boolean = false,
    val selectedPodcastGuids: ImmutableSet<String> = persistentSetOf()
)

// One-time events for the UI (e.g. Snackbars)
sealed interface HomeUiEvent {
    data class ShowUserMessage(val message: UiText) : HomeUiEvent
}

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val getAllPodcasts: GetAllPodcastsUseCase,
    private val getUserSettings: GetUserSettingsUseCase,
    private val refreshPodcasts: RefreshPodcastsUseCase,
    private val reorderPodcasts: ReorderPodcastsUseCase,
    private val markAllPodcastsSeen: MarkAllPodcastsSeenUseCase,
    private val deletePodcastUseCase: DeletePodcastUseCase,
    playerController: AudioPlayerController,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val didRunStartRefresh = AtomicBoolean(false)
    private val ownsManualRefreshPresentation = AtomicBoolean(false)
    private var lastStartRefreshAtMs: Long = 0L

    // Exposed "silent refresh" indicator for the TopBar
    private val _isAutoRefreshing = MutableStateFlow(false)
    val isAutoRefreshing: StateFlow<Boolean> = _isAutoRefreshing.asStateFlow()

    // Internal Mutable States
    private val _editState = MutableStateFlow(EditState())
    private val _isRefreshing = MutableStateFlow(false)

    private val _showDeleteConfirmation = MutableStateFlow(false)

    private val _screenError = MutableStateFlow<UiText?>(null)

    // PERFORMANCE: optimistic cache for drag & drop
    private val _optimisticPodcasts = MutableStateFlow<List<Podcast>?>(null)

    // Event channel for one-shot UI actions (Snackbars)
    private val _events = Channel<HomeUiEvent>()
    val events = _events.receiveAsFlow()

    // Player state flow uses the database episode ID.
    private val isPlayerVisibleFlow =
        playerController.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()

    private val podcastsFlow =
        getAllPodcasts()
            .map { it.toImmutableList() }
            .distinctUntilChanged()
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    // Stage 1: combine data into an intermediate state
    private val intermediateStateFlow: Flow<IntermediateHomeState> =
        combine(
            podcastsFlow,
            _optimisticPodcasts,
            getUserSettings(),
            _editState,
            _isRefreshing,
            isPlayerVisibleFlow,
            _showDeleteConfirmation,
            _screenError
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val podcastLoad = args[0] as RetainedLoad<ImmutableList<Podcast>>
            val dbPodcasts = podcastLoad.lastValue ?: emptyList<Podcast>().toImmutableList()

            @Suppress("UNCHECKED_CAST")
            val optimisticPodcasts = args[1] as List<Podcast>?
            val settings = args[2] as UserSettings
            val editState = args[3] as EditState
            val isRefreshing = args[4] as Boolean
            val isPlayerVisible = args[5] as Boolean
            val showDeleteConfirmation = args[6] as Boolean
            val screenError = args[7] as UiText?

            // If optimistic data exists (during drag & drop), use it.
            val finalPodcasts =
                if (optimisticPodcasts != null && optimisticPodcasts.size == dbPodcasts.size) {
                    optimisticPodcasts.toImmutableList()
                } else {
                    dbPodcasts
                }

            IntermediateHomeState(
                podcasts = finalPodcasts,
                contentLoad = if (podcastLoad.lastValue == null) {
                    podcastLoad
                } else {
                    podcastLoad.copy(lastValue = finalPodcasts)
                },
                settings = settings,
                editState = editState,
                isRefreshing = isRefreshing,
                isPlayerVisible = isPlayerVisible,
                showDeleteConfirmation = showDeleteConfirmation,
                screenError = screenError
            )
        }

    // Stage 2: produce the final UI state
    val uiState: StateFlow<HomeUiState> =
        intermediateStateFlow
            .map { state ->
                // Compute the list of currently selected podcasts for the UI
                val selectedPodcasts =
                    if (state.editState.selectedPodcastGuids.isNotEmpty()) {
                        state.podcasts.filter { it.rssUrl in state.editState.selectedPodcastGuids }
                    } else {
                        emptyList()
                    }

                HomeUiState(
                    contentLoad = state.contentLoad,
                    podcasts = state.podcasts,
                    isLoading = state.contentLoad.loading,
                    layoutMode = state.settings.layoutMode,
                    gridSize = state.settings.gridSize,
                    showGridTitles = state.settings.showGridTitles,
                    transparentPodcastCards = state.settings.transparentPodcastCards,
                    oneHandedMode = state.settings.oneHandedMode,
                    isEditMode = state.editState.isEditMode,
                    selectedPodcastGuids = state.editState.selectedPodcastGuids,
                    confirmDelete = state.settings.confirmDelete,
                    indicatorColorArgb = state.settings.indicator.colorArgb,
                    indicatorSize = state.settings.indicator.size,
                    indicatorBorderWidth = state.settings.indicator.borderWidth,
                    indicatorXOffset = state.settings.indicator.xOffset,
                    indicatorYOffset = state.settings.indicator.yOffset,
                    isRefreshing = state.isRefreshing,
                    progressBarHeight = state.settings.progressBarHeight,
                    navBarHeight = state.settings.navBarHeight,
                    isPlayerVisible = state.isPlayerVisible,
                    userMessage = null,
                    showDeleteConfirmation = state.showDeleteConfirmation,
                    selectedPodcastsForDelete = selectedPodcasts,
                    screenError = state.screenError
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
                initialValue = HomeUiState(isLoading = true)
            )

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
                val summary = refreshPodcasts(forceFull = true)
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
                selectedPodcastGuids = persistentSetOf(initialPodcastUrl)
            )
        }
    }

    fun exitEditMode() {
        _editState.update { it.copy(isEditMode = false, selectedPodcastGuids = persistentSetOf()) }
        _optimisticPodcasts.value = null
    }

    fun onReorder(
        fromIndex: Int,
        toIndex: Int
    ) {
        val currentList = uiState.value.podcasts.toMutableList()

        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _optimisticPodcasts.value = currentList.toList()

            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    reorderPodcasts(currentList)
                } catch (e: CancellationException) {
                    throw e // structured concurrency requires this
                } catch (e: Exception) {
                    _events.send(HomeUiEvent.ShowUserMessage(UiText.StringResource(R.string.reorder_error)))
                    _optimisticPodcasts.value = null
                }
            }
        }
    }

    fun toggleSelection(podcastUrl: String) {
        _editState.update { state ->
            val current = state.selectedPodcastGuids
            // '+' and '-' create new ImmutableSets
            val newSet =
                if (current.contains(podcastUrl)) {
                    current - podcastUrl
                } else {
                    current + podcastUrl
                }
            state.copy(selectedPodcastGuids = newSet.toImmutableSet())
        }
    }

    fun onPodcastInteract(targetUrl: String) {
        if (!uiState.value.isEditMode) return
        toggleSelection(targetUrl)
    }

    fun onDeleteSelectedRequest() {
        val selectedCount = _editState.value.selectedPodcastGuids.size
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
        val guidsToDelete = _editState.value.selectedPodcastGuids
        if (guidsToDelete.isEmpty()) return

        val podcastsToDelete = uiState.value.podcasts.filter { it.rssUrl in guidsToDelete }

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
