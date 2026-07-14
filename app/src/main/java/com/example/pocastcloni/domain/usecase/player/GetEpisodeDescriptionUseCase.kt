package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.domain.repository.PodcastQueryPort
import javax.inject.Inject

class GetEpisodeDescriptionUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort
) {
    suspend operator fun invoke(episodeId: Long): String? {
        return podcastQuery.getEpisode(episodeId)?.description
    }
}
