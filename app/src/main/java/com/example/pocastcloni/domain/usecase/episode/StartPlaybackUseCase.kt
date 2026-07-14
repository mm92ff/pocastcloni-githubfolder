package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.playback.api.PlaybackStarter
import javax.inject.Inject

class StartPlaybackUseCase
@Inject
constructor(
    private val playbackStarter: PlaybackStarter
) {
    suspend operator fun invoke(episodeId: Long) {
        playbackStarter.play(episodeId)
    }
}
