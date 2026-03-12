package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoriteEpisodesUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository
) {
    operator fun invoke(): Flow<List<EpisodeEntity>> {
        return podcastRepository.getFavoriteEpisodes()
    }
}