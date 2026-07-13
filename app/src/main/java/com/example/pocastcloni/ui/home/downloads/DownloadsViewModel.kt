package com.example.pocastcloni.ui.home.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.domain.usecase.episode.GetDownloadedEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.StartPlaybackUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.home.detail.toEpisodeUiModel
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerBits(
    val currentEpisodeId: Long?,
    val isPlaying: Boolean,
    val isVisible: Boolean
)

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    playerController: AudioPlayerController,
    private val downloader: DownloadEpisodeUseCase,
    userPreferencesRepository: UserPreferencesRepository,
    getDownloadedEpisodesWithPodcastInfo: GetDownloadedEpisodesWithPodcastInfoUseCase,
    private val startPlaybackUseCase: StartPlaybackUseCase,
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _episodeToDelete = MutableStateFlow<EpisodeUiModel?>(null)

    private val downloadedEpisodesFlow =
        getDownloadedEpisodesWithPodcastInfo()
            .map { episodes ->
                episodes.map { episodeInfo ->
                    episodeInfo.toEpisodeUiModel(downloadProgress = 1.0f)
                }.toImmutableList()
            }
            .distinctUntilChanged()
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    private val playerBitsFlow =
        playerController.playerState
            .map { ps ->
                PlayerBits(
                    currentEpisodeId = ps.currentEpisodeId,
                    isPlaying = ps.isPlaying,
                    isVisible = ps.currentEpisodeId != null
                )
            }
            .distinctUntilChanged()

    val uiState: StateFlow<DownloadsUiState> =
        combine(
            downloadedEpisodesFlow,
            userPreferencesRepository.userSettingsFlow,
            playerBitsFlow,
            _episodeToDelete
        ) { contentLoad, settings, playerBits, episodeToDelete ->
            val downloadedEpisodes = contentLoad.lastValue ?: kotlinx.collections.immutable.persistentListOf()
            DownloadsUiState(
                contentLoad = contentLoad,
                isLoading = contentLoad.loading,
                episodes = downloadedEpisodes,
                oneHandedMode = settings.oneHandedMode,
                confirmDelete = settings.confirmDelete,
                isPlayerPlaying = playerBits.isPlaying,
                currentPlayingEpisodeId = playerBits.currentEpisodeId,
                progressBarHeight = settings.progressBarHeight,
                navBarHeight = settings.navBarHeight,
                isPlayerVisible = playerBits.isVisible,
                episodeToDelete = episodeToDelete
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = DownloadsUiState()
        )

    fun playEpisode(episode: EpisodeUiModel) {
        viewModelScope.launch {
            startPlaybackUseCase(episode.episodeId)
        }
    }

    fun onFavoriteToggle(episode: EpisodeUiModel) {
        viewModelScope.launch {
            toggleFavoriteEpisodeUseCase(episode.episodeId, episode.isFavorite)
        }
    }

    fun deleteEpisode(episode: EpisodeUiModel) {
        if (uiState.value.confirmDelete) {
            _episodeToDelete.value = episode
        } else {
            performDelete(episode)
        }
    }

    fun confirmDelete() {
        _episodeToDelete.value?.let { performDelete(it) }
        cancelDelete()
    }

    fun cancelDelete() {
        _episodeToDelete.value = null
    }

    private fun performDelete(episode: EpisodeUiModel) {
        viewModelScope.launch(dispatcherProvider.io) {
            downloader(episode.episodeId)
        }
    }
}
