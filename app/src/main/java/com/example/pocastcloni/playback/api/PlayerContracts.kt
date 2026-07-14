package com.example.pocastcloni.playback.api

import com.example.pocastcloni.util.Constants

sealed interface PlayerScreenEvent {
    data object TogglePlayPause : PlayerScreenEvent

    data object Rewind : PlayerScreenEvent

    data object Forward : PlayerScreenEvent

    data class SeekTo(val positionMs: Long) : PlayerScreenEvent

    data object SeekStarted : PlayerScreenEvent

    data object SeekFinished : PlayerScreenEvent

    data object ShowDescription : PlayerScreenEvent

    data object DismissDescription : PlayerScreenEvent

    data object ToggleFavorite : PlayerScreenEvent
}

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentEpisodeTitle: String = Constants.EMPTY_STRING,
    val currentEpisodeSubtitle: String = Constants.EMPTY_STRING,
    val coverUrl: String = Constants.EMPTY_STRING,
    val currentEpisodeId: Long? = null,
    val currentPodcastUrl: String? = null,
    val error: String? = null,
    val isCurrentEpisodeFavorite: Boolean = false
)

data class PlaybackState(
    val currentPositionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val durationMs: Long = 0
)
