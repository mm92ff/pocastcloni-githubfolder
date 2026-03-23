package com.example.pocastcloni.ui.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Vorschlag 2: Trennung von Commands und State.
 */

// Interface für UI-Komponenten, die den Player STEUERN (Schreiben)
interface PlayerActions {
    suspend fun play(episodeGuid: String)

    fun pause()

    fun resume()

    fun onEvent(event: PlayerScreenEvent)

    fun releaseResources()
}

// Interface für UI-Komponenten, die den Player nur ANZEIGEN (Lesen)
interface PlayerStateObserver {
    val playerState: StateFlow<PlayerUiState>
    val playbackState: StateFlow<PlaybackState>
}
