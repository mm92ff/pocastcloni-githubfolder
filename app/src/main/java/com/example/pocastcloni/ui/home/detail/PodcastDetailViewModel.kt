package com.example.pocastcloni.ui.home.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.domain.usecase.episode.StartPlaybackUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleEpisodePlayedStatusUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.podcast.UpdatePodcastAutoDownloadUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.ui.navigation.Screen
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.ui.settings.ThemeUiModel
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.stripHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed class PodcastDetailAction {
    data class PlayEpisode(val episodeId: Long) : PodcastDetailAction()

    data class ToggleDownload(val episodeId: Long) : PodcastDetailAction()

    data class TogglePlayedStatus(val episodeId: Long) : PodcastDetailAction()

    data class ToggleFavorite(val episodeId: Long) : PodcastDetailAction()

    data class ToggleAutoDownload(val enabled: Boolean) : PodcastDetailAction()

    data object ShowPodcastDescription : PodcastDetailAction()

    data object DismissPodcastDescription : PodcastDetailAction()

    data object NavigateBack : PodcastDetailAction()
}

@HiltViewModel
class PodcastDetailViewModel
@Inject
@Suppress("LongParameterList")
constructor(
    savedStateHandle: SavedStateHandle,
    podcastQuery: PodcastQueryPort,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerCommandPort: PlayerCommandPort,
    playerStatePort: PlayerStatePort,
    private val downloader: DownloadEpisodeUseCase,
    private val startPlaybackUseCase: StartPlaybackUseCase,
    private val toggleEpisodePlayedStatusUseCase: ToggleEpisodePlayedStatusUseCase,
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val updatePodcastAutoDownloadUseCase: UpdatePodcastAutoDownloadUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val podcastUrl: String =
        URLDecoder.decode(
            checkNotNull(savedStateHandle.get<String>(Screen.PODCAST_URL)),
            StandardCharsets.UTF_8.name()
        )

    private val _userMessageChannel = Channel<UiText>()
    val userMessageFlow = _userMessageChannel.receiveAsFlow()

    private val _isPodcastDescriptionDialogVisible = MutableStateFlow(false)

    private val podcastDetailsFlow =
        combine(
            podcastQuery.getPodcastFlow(podcastUrl),
            podcastQuery.getEpisodesFlow(podcastUrl),
            downloader.downloadProgressFlow
        ) { podcast, episodes, progressMap ->
            if (podcast == null) {
                PodcastDetailContent(error = UiText.StringResource(R.string.detail_not_found))
            } else {
                val podcastTitle = podcast.title.stripHtml()
                val podcastImageUrl = podcast.imageUrl

                val newList =
                    episodes.map { entity ->
                        val presentation = EpisodePresentation.from(entity)
                        presentation.toEpisodeUiModel(
                            podcastName = podcastTitle,
                            podcastImageUrl = podcastImageUrl,
                            downloadProgress = progressMap[entity.episodeId] ?: 0f
                        )
                    }.toImmutableList()

                PodcastDetailContent(
                    title = podcastTitle,
                    description = podcast.description.stripHtml(),
                    imageUrl = podcastImageUrl,
                    autoDownloadEnabled = podcast.autoDownloadEnabled,
                    episodes = newList,
                    error = null
                )
            }
        }.flowOn(dispatcherProvider.default)
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    private val settingsUiModelFlow =
        userPreferencesRepository.userSettingsFlow
            .map { settings ->
                SettingsUiModel(
                    theme =
                    when (settings.theme) {
                        AppTheme.LIGHT -> ThemeUiModel.LIGHT
                        AppTheme.DARK -> ThemeUiModel.DARK
                        else -> ThemeUiModel.SYSTEM
                    },
                    oneHandedMode = settings.oneHandedMode,
                    navBarHeight = settings.navBarHeight,
                    progressBarHeight = settings.progressBarHeight
                )
            }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.default)

    private val playerStatusFlow =
        playerStatePort.playerState
            .map { playerState ->
                PlayerStatusUiState(
                    currentPlayingEpisodeId = playerState.currentEpisodeId,
                    isPlayerPlaying = playerState.isPlaying,
                    isPlayerVisible = playerState.currentEpisodeId != null
                )
            }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.default)

    val uiState: StateFlow<PodcastDetailUiState> =
        combine(
            podcastDetailsFlow,
            settingsUiModelFlow,
            playerStatusFlow,
            _isPodcastDescriptionDialogVisible
        ) { contentLoad, settingsUiModel, playerStatus, isDialogVisible ->
            val details = contentLoad.lastValue ?: PodcastDetailContent()
            PodcastDetailUiState(
                contentLoad = contentLoad,
                podcastTitle = details.title,
                podcastDescription = details.description,
                podcastImageUrl = details.imageUrl,
                isAutoDownloadEnabled = details.autoDownloadEnabled,
                episodes = details.episodes,
                isLoading = contentLoad.loading,
                error = details.error ?: contentLoad.error?.takeIf { contentLoad.lastValue == null },
                isPodcastDescriptionDialogVisible = isDialogVisible,
                playerState = playerStatus,
                appSettings = settingsUiModel
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = PodcastDetailUiState(isLoading = true)
        )

    fun onAction(action: PodcastDetailAction) {
        when (action) {
            is PodcastDetailAction.PlayEpisode -> playEpisode(action.episodeId)
            is PodcastDetailAction.ToggleDownload -> toggleDownload(action.episodeId)
            is PodcastDetailAction.TogglePlayedStatus -> togglePlayed(action.episodeId)
            is PodcastDetailAction.ToggleFavorite -> toggleFavorite(action.episodeId)
            is PodcastDetailAction.ToggleAutoDownload -> toggleAutoDownload(action.enabled)
            PodcastDetailAction.ShowPodcastDescription -> _isPodcastDescriptionDialogVisible.update { true }
            PodcastDetailAction.DismissPodcastDescription -> _isPodcastDescriptionDialogVisible.update { false }
            PodcastDetailAction.NavigateBack -> { /* handled by UI */ }
        }
    }

    private fun playEpisode(episodeId: Long) {
        val playerStatus = uiState.value.playerState

        if (playerStatus.currentPlayingEpisodeId == episodeId && playerStatus.isPlayerPlaying) {
            playerCommandPort.onEvent(PlayerScreenEvent.TogglePlayPause)
        } else {
            viewModelScope.launch {
                startPlaybackUseCase(episodeId)
            }
        }
    }

    private fun togglePlayed(episodeId: Long) {
        viewModelScope.launch(dispatcherProvider.io) {
            toggleEpisodePlayedStatusUseCase(episodeId)
        }
    }

    private fun toggleFavorite(episodeId: Long) {
        viewModelScope.launch {
            val episode = uiState.value.episodes.find { it.episodeId == episodeId } ?: return@launch
            toggleFavoriteEpisodeUseCase(episodeId, episode.isFavorite)
        }
    }

    private fun toggleAutoDownload(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updatePodcastAutoDownloadUseCase(podcastUrl, enabled)
        }
    }

    private fun toggleDownload(episodeId: Long) {
        viewModelScope.launch(dispatcherProvider.io) {
            downloader(episodeId)
        }
    }
}
