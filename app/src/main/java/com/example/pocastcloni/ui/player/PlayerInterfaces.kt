package com.example.pocastcloni.ui.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Separation of commands and state.
 */

// Interface for UI components that CONTROL the player (write)
interface PlayerActions {
    suspend fun play(episodeGuid: String)

    fun pause()

    fun resume()

    fun onEvent(event: PlayerScreenEvent)

    fun releaseResources()
}

// Interface for UI components that only OBSERVE the player (read)
interface PlayerStateObserver {
    val playerState: StateFlow<PlayerUiState>
    val playbackState: StateFlow<PlaybackState>
}
