package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.compose.runtime.Stable
import com.example.pocastcloni.util.Constants

sealed interface PlayerScreenEvent {
    data object TogglePlayPause : PlayerScreenEvent

    data object Rewind : PlayerScreenEvent

    data object Forward : PlayerScreenEvent

    data class SeekTo(val positionMs: Long) : PlayerScreenEvent

    // NEW: used to pause frequent progress emissions while user scrubs
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
    val episodeDescription: Spanned? = null,
    val isDescriptionDialogVisible: Boolean = false,
    val isParsingDescription: Boolean = false,
    val error: String? = null,
    val isCurrentEpisodeFavorite: Boolean = false
)

@Stable
data class PlaybackState(
    val currentPositionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val durationMs: Long = 0
)
