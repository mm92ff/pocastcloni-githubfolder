package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class SavePlaybackProgressUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    suspend operator fun invoke(
        episodeId: Long,
        positionMs: Long
    ) {
        if (episodeId > 0) {
            podcastCommands.savePlaybackProgress(episodeId, positionMs)
        }
    }
}
