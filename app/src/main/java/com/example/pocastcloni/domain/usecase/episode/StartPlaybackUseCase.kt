package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.ui.player.AudioPlayerController
import javax.inject.Inject

class StartPlaybackUseCase
@Inject
constructor(
    private val playerController: AudioPlayerController
) {
    suspend operator fun invoke(episodeGuid: String) {
        playerController.play(episodeGuid)
    }
}
