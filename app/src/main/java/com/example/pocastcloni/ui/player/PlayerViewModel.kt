package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.player.GetEpisodeDescriptionUseCase
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerStatePort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val playerCommandPort: PlayerCommandPort,
    playerStatePort: PlayerStatePort,
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val getEpisodeDescriptionUseCase: GetEpisodeDescriptionUseCase
) : ViewModel() {
    val playerState = playerStatePort.playerState
    val playbackState = playerStatePort.playbackState

    // UI State for Description
    private val _descriptionState = MutableStateFlow<Spanned?>(null)
    val descriptionState: StateFlow<Spanned?> = _descriptionState.asStateFlow()

    private val _isDescriptionVisible = MutableStateFlow(false)
    val isDescriptionVisible: StateFlow<Boolean> = _isDescriptionVisible.asStateFlow()

    fun handlePlayerEvent(event: PlayerScreenEvent) {
        when (event) {
            PlayerScreenEvent.ToggleFavorite -> {
                val episodeId = playerState.value.currentEpisodeId ?: return
                val currentlyFav = playerState.value.isCurrentEpisodeFavorite
                viewModelScope.launch(dispatcherProvider.io) {
                    runCatching { toggleFavoriteEpisodeUseCase(episodeId, currentlyFav) }
                        .onFailure { Timber.e(it, "Failed to toggle favorite") }
                }
            }
            PlayerScreenEvent.ShowDescription -> loadDescription()
            PlayerScreenEvent.DismissDescription -> {
                _isDescriptionVisible.value = false
                _descriptionState.value = null
            }
            else -> playerCommandPort.onEvent(event)
        }
    }

    private fun loadDescription() {
        val episodeId = playerState.value.currentEpisodeId ?: return
        _isDescriptionVisible.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            val rawHtml = runCatching { getEpisodeDescriptionUseCase(episodeId) }
                .onFailure { Timber.e(it, "Failed to load episode description for id: $episodeId") }
                .getOrNull()
            val spanned = rawHtml?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
            _descriptionState.value = spanned
        }
    }
}
