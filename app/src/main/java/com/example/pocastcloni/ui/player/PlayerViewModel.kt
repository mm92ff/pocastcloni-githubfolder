package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.player.GetEpisodeDescriptionUseCase
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
    val playerController: AudioPlayerController, // Implementiert jetzt PlayerActions & Observer
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val getEpisodeDescriptionUseCase: GetEpisodeDescriptionUseCase
) : ViewModel() {
    // UI State for Description
    private val _descriptionState = MutableStateFlow<Spanned?>(null)
    val descriptionState: StateFlow<Spanned?> = _descriptionState.asStateFlow()

    private val _isDescriptionVisible = MutableStateFlow(false)
    val isDescriptionVisible: StateFlow<Boolean> = _isDescriptionVisible.asStateFlow()

    init {
        // Clean: Keine manuellen 'connectIfNeeded()' Aufrufe mehr nötig!
        // Der Controller verbindet sich automatisch, sobald die UI auf 'playerController.playerState' zugreift (via Compose).
    }

    fun handlePlayerEvent(event: PlayerScreenEvent) {
        when (event) {
            PlayerScreenEvent.ToggleFavorite -> {
                val guid = playerController.playerState.value.currentEpisodeGuid ?: return
                val currentlyFav = playerController.playerState.value.isCurrentEpisodeFavorite
                viewModelScope.launch(dispatcherProvider.io) {
                    runCatching { toggleFavoriteEpisodeUseCase(guid, currentlyFav) }
                        .onFailure { Timber.e(it, "Failed to toggle favorite") }
                }
            }
            PlayerScreenEvent.ShowDescription -> loadDescription()
            PlayerScreenEvent.DismissDescription -> {
                _isDescriptionVisible.value = false
                _descriptionState.value = null
            }
            else -> playerController.onEvent(event)
        }
    }

    private fun loadDescription() {
        val guid = playerController.playerState.value.currentEpisodeGuid ?: return
        _isDescriptionVisible.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            val rawHtml = runCatching { getEpisodeDescriptionUseCase(guid) }.getOrNull()
            val spanned = rawHtml?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
            _descriptionState.value = spanned
        }
    }
}
