package com.example.pocastcloni.ui.home.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.podcast.DeletePodcastUseCase
import com.example.pocastcloni.domain.usecase.podcast.GetAllPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.MarkAllPodcastsSeenUseCase
import com.example.pocastcloni.domain.usecase.podcast.RefreshPodcastsUseCase
import com.example.pocastcloni.domain.usecase.podcast.ReorderPodcastsUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

// Interne State-Repräsentation (Clean & Type-Safe)
private data class IntermediateHomeState(
    val podcasts: ImmutableList<Podcast>,
    val settings: UserSettings,
    val editState: EditState,
    val isRefreshing: Boolean,
    val isPlayerVisible: Boolean,
    // FIX: showDeleteConfirmation statt podcastToDelete
    val showDeleteConfirmation: Boolean,
    val screenError: UiText?
)

private data class EditState(
    val isEditMode: Boolean = false,
    // FIX: Set statt String
    val selectedPodcastGuids: ImmutableSet<String> = persistentSetOf()
)

// One-Time Events für die UI (z.B. Snackbars)
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
    private val refreshMutex = Mutex()
    private val didRunStartRefresh = AtomicBoolean(false)
    private var lastStartRefreshAtMs: Long = 0L

    // Exposed "silent refresh" indicator for the TopBar
    private val _isAutoRefreshing = MutableStateFlow(false)
    val isAutoRefreshing: StateFlow<Boolean> = _isAutoRefreshing.asStateFlow()

    // Internal Mutable States
    private val _editState = MutableStateFlow(EditState())
    private val _isRefreshing = MutableStateFlow(false)

    // FIX: Boolescher Flag für Dialog Sichtbarkeit
    private val _showDeleteConfirmation = MutableStateFlow(false)

    private val _screenError = MutableStateFlow<UiText?>(null)

    // PERFORMANCE: Optimistischer Cache für Drag & Drop
    private val _optimisticPodcasts = MutableStateFlow<List<Podcast>?>(null)

    // Event Channel für einmalige UI-Aktionen (Snackbars)
    private val _events = Channel<HomeUiEvent>()
    val events = _events.receiveAsFlow()

    // Player State Flow (GUID-basiert, stabil)
    private val isPlayerVisibleFlow =
        playerController.playerState
            .map { !it.currentEpisodeGuid.isNullOrBlank() }
            .distinctUntilChanged()

    private val podcastsFlow =
        getAllPodcasts()
            .map { it.toImmutableList() }
            .distinctUntilChanged()

    // 1. Stage: Daten kombinieren (Intermediate State)
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
            val dbPodcasts = args[0] as ImmutableList<Podcast>

            @Suppress("UNCHECKED_CAST")
            val optimisticPodcasts = args[1] as List<Podcast>?
            val settings = args[2] as UserSettings
            val editState = args[3] as EditState
            val isRefreshing = args[4] as Boolean
            val isPlayerVisible = args[5] as Boolean
            val showDeleteConfirmation = args[6] as Boolean
            val screenError = args[7] as UiText?

            // Wenn optimistische Daten existieren (während Drag & Drop), nutzen wir diese.
            val finalPodcasts =
                if (optimisticPodcasts != null && optimisticPodcasts.size == dbPodcasts.size) {
                    optimisticPodcasts.toImmutableList()
                } else {
                    dbPodcasts
                }

            IntermediateHomeState(
                podcasts = finalPodcasts,
                settings = settings,
                editState = editState,
                isRefreshing = isRefreshing,
                isPlayerVisible = isPlayerVisible,
                showDeleteConfirmation = showDeleteConfirmation,
                screenError = screenError
            )
        }

    // 2. Stage: Finaler UI State
    val uiState: StateFlow<HomeUiState> =
        intermediateStateFlow
            .map { state ->
                // Helper: Berechne die Liste der aktuell selektierten Podcasts für die UI
                val selectedPodcasts =
                    if (state.editState.selectedPodcastGuids.isNotEmpty()) {
                        state.podcasts.filter { it.rssUrl in state.editState.selectedPodcastGuids }
                    } else {
                        emptyList()
                    }

                HomeUiState(
                    podcasts = state.podcasts,
                    isLoading = false,
                    // -------------------------------------------------------------
                    // KORREKTUR: Layout Mode wird jetzt korrekt übertragen!
                    // -------------------------------------------------------------
                    layoutMode = state.settings.layoutMode,
                    gridSize = state.settings.gridSize,
                    showGridTitles = state.settings.showGridTitles,
                    oneHandedMode = state.settings.oneHandedMode,
                    isEditMode = state.editState.isEditMode,
                    // FIX: Set übertragen
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
                    // FIX: Dialog State und Liste
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
        viewModelScope.launch(dispatcherProvider.io) {
            refreshMutex.withLock {
                _isRefreshing.value = true
                _screenError.value = null
                try {
                    val errorText = UiText.StringResource(R.string.refresh_error)
                    val summary = refreshPodcasts(forceFull = false)
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
                } catch (e: Exception) {
                    val errorText = UiText.StringResource(R.string.refresh_error)
                    if (uiState.value.podcasts.isEmpty()) {
                        _screenError.value = errorText
                    } else {
                        _events.send(HomeUiEvent.ShowUserMessage(errorText))
                    }
                } finally {
                    _isRefreshing.value = false
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
            val lockAcquired = refreshMutex.tryLock()
            if (!lockAcquired) return@launch

            try {
                val settings = getUserSettings().first()
                if (!settings.autoRefreshOnStart) return@launch
                if (_isRefreshing.value) return@launch

                _isAutoRefreshing.value = true
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
                } catch (e: Exception) {
                    Timber.w(e, "Auto refresh on start failed")
                } finally {
                    _isAutoRefreshing.value = false
                }
            } catch (e: Exception) {
                _isAutoRefreshing.value = false
                Timber.w(e, "Auto refresh on start failed (unexpected)")
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    fun enterEditMode(initialPodcastUrl: String) {
        _editState.update {
            it.copy(
                isEditMode = true,
                // FIX: Set initialisieren
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
                } catch (e: Exception) {
                    _events.send(HomeUiEvent.ShowUserMessage(UiText.StringResource(R.string.reorder_error)))
                    _optimisticPodcasts.value = null
                }
            }
        }
    }

    // FIX: Multi-Select Toggle Logik mit korrekten ImmutableSet Operatoren
    fun toggleSelection(podcastUrl: String) {
        _editState.update { state ->
            val current = state.selectedPodcastGuids
            // Nutzung von '+' und '-' erstellt neue ImmutableSets
            val newSet =
                if (current.contains(podcastUrl)) {
                    current - podcastUrl
                } else {
                    current + podcastUrl
                }
            // Wichtig: Explizit in ImmutableSet wandeln, um Typfehler zu vermeiden
            state.copy(selectedPodcastGuids = newSet.toImmutableSet())
        }
    }

    // FIX: Aufruf durch ToggleSelection ersetzt
    fun onPodcastInteract(targetUrl: String) {
        if (!uiState.value.isEditMode) return
        toggleSelection(targetUrl)
    }

    // FIX: Batch Delete Logik
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

    // FIX: Batch Delete Ausführung
    private fun executeDeleteSelected() {
        val guidsToDelete = _editState.value.selectedPodcastGuids
        if (guidsToDelete.isEmpty()) return

        val podcastsToDelete = uiState.value.podcasts.filter { it.rssUrl in guidsToDelete }

        viewModelScope.launch(dispatcherProvider.io) {
            podcastsToDelete.forEach { podcast ->
                try {
                    deletePodcastUseCase(podcast)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete podcast ${podcast.title}")
                }
            }
            // Edit Mode verlassen nach erfolgreichem Löschen
            exitEditMode()
        }
    }
}
