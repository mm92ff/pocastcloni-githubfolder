package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ReorderFavoritesUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(reorderedEpisodeIds: List<Long>) {
        val timestamp = System.currentTimeMillis()
        val episodes =
            reorderedEpisodeIds.map { episodeId ->
                repository.getEpisode(episodeId)
                    ?: throw IllegalStateException("Episode not found for ID $episodeId")
            }
        val updatedList =
            episodes.mapIndexed { index, episode ->
                episode.copy(favoriteTimestamp = timestamp - index)
            }
        repository.reorderFavorites(updatedList)
    }
}
