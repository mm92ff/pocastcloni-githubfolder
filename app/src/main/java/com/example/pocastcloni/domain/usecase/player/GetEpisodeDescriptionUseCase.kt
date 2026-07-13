package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class GetEpisodeDescriptionUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(episodeId: Long): String? {
        return repository.getEpisode(episodeId)?.description
    }
}
