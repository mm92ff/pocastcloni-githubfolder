package com.example.pocastcloni.ui.player

import com.example.pocastcloni.domain.player.PlayerVisibilityProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerVisibilityProviderImpl
@Inject
constructor(
    private val playerController: AudioPlayerController
) : PlayerVisibilityProvider {
    override val isPlayerVisible: Flow<Boolean> =
        playerController.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()
}
