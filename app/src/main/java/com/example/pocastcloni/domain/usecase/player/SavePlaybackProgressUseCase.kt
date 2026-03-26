package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class SavePlaybackProgressUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(
        episodeGuid: String,
        positionMs: Long
    ) {
        if (episodeGuid.isNotBlank()) {
            repository.savePlaybackProgress(episodeGuid, positionMs)
        }
    }
}
