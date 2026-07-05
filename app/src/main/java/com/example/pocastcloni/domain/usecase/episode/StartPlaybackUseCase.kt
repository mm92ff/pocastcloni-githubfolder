package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.player.PlaybackStarter
import javax.inject.Inject

class StartPlaybackUseCase
@Inject
constructor(
    private val playbackStarter: PlaybackStarter
) {
    suspend operator fun invoke(episodeGuid: String) {
        playbackStarter.play(episodeGuid)
    }
}
