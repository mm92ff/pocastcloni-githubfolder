package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class GetEpisodeDescriptionUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(guid: String): String? {
        return repository.getEpisode(guid)?.description
    }
}
