package com.example.pocastcloni.playback.infrastructure

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.playback.api.PlaybackResetPort
import com.example.pocastcloni.playback.api.PlayerCommandPort
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackResetCoordinator
@Inject
constructor(
    private val mediaConnection: MediaControllerConnection,
    private val playerCommands: PlayerCommandPort,
    private val dispatcherProvider: DispatcherProvider
) : PlaybackResetPort {
    override suspend fun stopAndReleaseForReset() {
        withContext(dispatcherProvider.main) {
            try {
                mediaConnection.activeController?.run {
                    stop()
                    clearMediaItems()
                }
            } finally {
                playerCommands.releaseResources()
            }
        }
    }
}
