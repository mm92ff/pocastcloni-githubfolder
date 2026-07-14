package com.example.pocastcloni.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.GetPlaybackHistoryWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.history.ClearHistoryUseCase
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.playback.api.PlaybackStarter
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    getPlaybackHistoryWithPodcastInfoUseCase: GetPlaybackHistoryWithPodcastInfoUseCase,
    private val playbackStarter: PlaybackStarter,
    playerStatePort: PlayerStatePort,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val _showConfirmClearDialog = MutableStateFlow(false)

    private val historyItemsFlow =
        getPlaybackHistoryWithPodcastInfoUseCase()
            .map { list ->
                list.map { info ->
                    HistoryUiItem(
                        id = info.episode.episodeId,
                        episode = EpisodeDisplayModel.from(info.episode, info.podcast),
                        podcast = info.podcast
                    )
                }.sortedByDescending { it.episode.datePlayedMs ?: 0L }
            }
            .distinctUntilChanged()
            .map { it.toImmutableList() }
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    private val isPlayerVisibleFlow =
        playerStatePort.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()

    val uiState =
        combine(
            historyItemsFlow,
            userPreferencesRepository.userSettingsFlow,
            isPlayerVisibleFlow,
            _showConfirmClearDialog
        ) { contentLoad, settings, isPlayerVisible, showConfirmClearDialog ->
            val historyItems = contentLoad.lastValue ?: kotlinx.collections.immutable.persistentListOf()
            HistoryUiState(
                contentLoad = contentLoad,
                isLoading = contentLoad.loading,
                historyItems = historyItems,
                historyRows = buildHistoryRows(
                    items = if (settings.oneHandedMode) {
                        historyItems.asReversed()
                    } else {
                        historyItems
                    }
                ).toImmutableList(),
                oneHandedMode = settings.oneHandedMode,
                isPlayerVisible = isPlayerVisible,
                navBarHeight = settings.navBarHeight,
                progressBarHeight = settings.progressBarHeight,
                transparentEpisodeRows = settings.transparentEpisodeRows,
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
                    playbackStarter.play(action.episodeId)
                }
            }

            HistoryAction.ClearHistory -> {
                _showConfirmClearDialog.value = true
            }

            HistoryAction.ConfirmClearHistory -> {
                viewModelScope.launch {
                    clearHistoryUseCase()
                }
                _showConfirmClearDialog.value = false
            }

            HistoryAction.DismissClearHistoryDialog -> {
                _showConfirmClearDialog.value = false
            }
        }
    }
}
