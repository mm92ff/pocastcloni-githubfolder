package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.ui.player.AudioPlayerController
import javax.inject.Inject

class StartPlaybackUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val playerController: AudioPlayerController
) {
    suspend operator fun invoke(episodeGuid: String) {
        val episode = repository.getEpisode(episodeGuid) ?: return
        invoke(episode)
    }

    suspend fun invoke(episode: EpisodeEntity) {
        playerController.play(episode)
    }
}
