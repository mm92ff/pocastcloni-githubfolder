package com.example.pocastcloni.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.GetPlaybackHistoryWithPodcastInfoUseCase
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getPlaybackHistoryWithPodcastInfoUseCase: GetPlaybackHistoryWithPodcastInfoUseCase,
    private val audioPlayerController: AudioPlayerController,
    private val podcastRepository: PodcastRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _showConfirmClearDialog = MutableStateFlow(false)

    // Transformation Flow: Map -> UiItem List
    // Läuft im Flow Context (meist Default/IO), entlastet UI Thread
    private val historyItemsFlow = getPlaybackHistoryWithPodcastInfoUseCase()
        .map { map ->
            map.entries.map { (episode, podcast) ->
                HistoryUiItem(
                    id = episode.guid,
                    episode = episode,
                    podcast = podcast
                )
            }.sortedByDescending { it.episode.datePlayed } // Optional: Sortierung sicherstellen
        }
        .distinctUntilChanged()

    private val isPlayerVisibleFlow = audioPlayerController.playerState
        .map { !it.currentEpisodeGuid.isNullOrBlank() }
        .distinctUntilChanged()

    val uiState = combine(
        historyItemsFlow,
        userPreferencesRepository.userSettingsFlow,
        isPlayerVisibleFlow,
        _showConfirmClearDialog
    ) { historyItems, settings, isPlayerVisible, showConfirmClearDialog ->
        HistoryUiState(
            isLoading = false,
            // FIX: Umwandlung zu ImmutableList für UI-Skipping
            historyItems = historyItems.toImmutableList(),
            oneHandedMode = settings.oneHandedMode,
            isPlayerVisible = isPlayerVisible,
            navBarHeight = settings.navBarHeight,
            progressBarHeight = settings.progressBarHeight,
            showConfirmClearDialog = showConfirmClearDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
        initialValue = HistoryUiState()
    )

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.OnEpisodeClick -> {
                viewModelScope.launch {
                    audioPlayerController.play(action.episode)
                }
            }

            HistoryAction.ClearHistory -> {
                _showConfirmClearDialog.value = true
            }

            HistoryAction.ConfirmClearHistory -> {
                viewModelScope.launch {
                    podcastRepository.clearHistory()
                }
                _showConfirmClearDialog.value = false
            }

            HistoryAction.DismissClearHistoryDialog -> {
                _showConfirmClearDialog.value = false
            }
        }
    }
}