package com.example.pocastcloni.playback.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackStarter {
    suspend fun play(episodeId: Long)
}

interface PlayerCommandPort {
    fun pause()

    fun resume()

    fun onEvent(event: PlayerScreenEvent)

    fun releaseResources()
}

interface PlayerStatePort {
    val playerState: StateFlow<PlayerUiState>
    val playbackState: StateFlow<PlaybackState>
}

interface PlayerVisibilityProvider {
    val isPlayerVisible: Flow<Boolean>
}
