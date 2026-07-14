package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoriteEpisodesUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort
) {
    operator fun invoke(): Flow<List<Episode>> {
        return podcastQuery.getFavoriteEpisodes()
    }
}
