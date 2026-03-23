package com.example.pocastcloni.ui.home.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.domain.usecase.episode.StartPlaybackUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleEpisodePlayedStatusUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.podcast.UpdatePodcastAutoDownloadUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.navigation.Screen
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.PlayerScreenEvent
import com.example.pocastcloni.ui.settings.AppTheme
import com.example.pocastcloni.ui.settings.ThemeUiModel
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.stripHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed class PodcastDetailAction {
    data class PlayEpisode(val guid: String) : PodcastDetailAction()

    data class ToggleDownload(val guid: String) : PodcastDetailAction()

    data class TogglePlayedStatus(val guid: String) : PodcastDetailAction()

    data class ToggleFavorite(val guid: String) : PodcastDetailAction()

    data class ToggleAutoDownload(val enabled: Boolean) : PodcastDetailAction()

    data object ShowPodcastDescription : PodcastDetailAction()

    data object DismissPodcastDescription : PodcastDetailAction()

    data object NavigateBack : PodcastDetailAction()
}

@HiltViewModel
class PodcastDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerController: AudioPlayerController,
    private val downloader: DownloadEpisodeUseCase,
    private val startPlaybackUseCase: StartPlaybackUseCase,
    private val toggleEpisodePlayedStatusUseCase: ToggleEpisodePlayedStatusUseCase,
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val updatePodcastAutoDownloadUseCase: UpdatePodcastAutoDownloadUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    // FIX: StandardCharsets Korrektur (kein doppeltes StandardCharsets mehr)
    private val podcastUrl: String =
        URLDecoder.decode(
            checkNotNull(savedStateHandle.get<String>(Screen.PODCAST_URL)),
            StandardCharsets.UTF_8.name()
        )

    private val _userMessageChannel = Channel<UiText>()
    val userMessageFlow = _userMessageChannel.receiveAsFlow()

    private val _isPodcastDescriptionDialogVisible = MutableStateFlow(false)

    private data class PodcastDetails(
        val title: String = "",
        val description: String = "",
        val imageUrl: String? = null,
        val autoDownloadEnabled: Boolean = false,
        val episodes: ImmutableList<EpisodeUiModel> = persistentListOf(),
        val error: UiText? = null
    )

    private val podcastDetailsFlow =
        combine(
            repository.getPodcastFlow(podcastUrl),
            repository.getEpisodesFlow(podcastUrl),
            downloader.downloadProgressFlow
        ) { podcast, episodes, progressMap ->
            if (podcast == null) {
                PodcastDetails(error = UiText.StringResource(R.string.detail_not_found))
            } else {
                val podcastTitle = podcast.title.stripHtml()
                val podcastImageUrl = podcast.imageUrl

                val newList =
                    episodes.map { entity ->
                        val presentation = EpisodePresentation.from(entity)
                        // HINWEIS: Hier muss entity.toEpisodeUiModel den Parameter 'downloadProgress' akzeptieren.
                        // Stelle sicher, dass du PodcastDetailModels.kt bzw. den Mapper aktualisiert hast.
                        presentation.toEpisodeUiModel(
                            podcastName = podcastTitle,
                            podcastImageUrl = podcastImageUrl,
                            downloadProgress = progressMap[entity.guid] ?: 0f
                        )
                    }.toImmutableList()

                PodcastDetails(
                    title = podcastTitle,
                    description = podcast.description.stripHtml(),
                    imageUrl = podcastImageUrl,
                    autoDownloadEnabled = podcast.autoDownloadEnabled,
                    episodes = newList,
                    error = null
                )
            }
        }.catch { throwable ->
            Timber.e(throwable, "podcastDetailsFlow failed")
            emit(PodcastDetails(error = UiText.StringResource(R.string.error_unknown)))
        }.flowOn(dispatcherProvider.default)

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
        playerController.playerState
            .map { playerState ->
                PlayerStatusUiState(
                    currentPlayingGuid = playerState.currentEpisodeGuid ?: "",
                    isPlayerPlaying = playerState.isPlaying,
                    isPlayerVisible = !playerState.currentEpisodeGuid.isNullOrBlank()
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
        ) { details, settingsUiModel, playerStatus, isDialogVisible ->
            PodcastDetailUiState(
                podcastTitle = details.title,
                podcastDescription = details.description,
                podcastImageUrl = details.imageUrl,
                isAutoDownloadEnabled = details.autoDownloadEnabled,
                episodes = details.episodes,
                isLoading = false,
                error = details.error,
                isPodcastDescriptionDialogVisible = isDialogVisible,
                playerState = playerStatus,
                appSettings = settingsUiModel
            )
        }.catch { throwable ->
            Timber.e(throwable, "Error creating UI state")
            emit(PodcastDetailUiState(error = UiText.StringResource(R.string.error_unknown)))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = PodcastDetailUiState(isLoading = true)
        )

    fun onAction(action: PodcastDetailAction) {
        when (action) {
            is PodcastDetailAction.PlayEpisode -> playEpisode(action.guid)
            is PodcastDetailAction.ToggleDownload -> toggleDownload(action.guid)
            is PodcastDetailAction.TogglePlayedStatus -> togglePlayed(action.guid)
            is PodcastDetailAction.ToggleFavorite -> toggleFavorite(action.guid)
            is PodcastDetailAction.ToggleAutoDownload -> toggleAutoDownload(action.enabled)
            PodcastDetailAction.ShowPodcastDescription -> _isPodcastDescriptionDialogVisible.update { true }
            PodcastDetailAction.DismissPodcastDescription -> _isPodcastDescriptionDialogVisible.update { false }
            PodcastDetailAction.NavigateBack -> { /* handled by UI */ }
        }
    }

    private fun playEpisode(guid: String) {
        val playerStatus = uiState.value.playerState

        if (playerStatus.currentPlayingGuid == guid && playerStatus.isPlayerPlaying) {
            playerController.onEvent(PlayerScreenEvent.TogglePlayPause)
        } else {
            viewModelScope.launch {
                startPlaybackUseCase(guid)
            }
        }
    }

    private fun togglePlayed(guid: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            toggleEpisodePlayedStatusUseCase(guid)
        }
    }

    private fun toggleFavorite(guid: String) {
        viewModelScope.launch {
            val episode = uiState.value.episodes.find { it.guid == guid } ?: return@launch
            toggleFavoriteEpisodeUseCase(guid, episode.isFavorite)
        }
    }

    private fun toggleAutoDownload(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updatePodcastAutoDownloadUseCase(podcastUrl, enabled)
        }
    }

    private fun toggleDownload(guid: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            downloader(guid)
        }
    }
}
