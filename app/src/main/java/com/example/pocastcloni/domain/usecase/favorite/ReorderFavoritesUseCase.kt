package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ReorderFavoritesUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(reorderedGuids: List<String>) {
        val timestamp = System.currentTimeMillis()
        val episodes =
            reorderedGuids.map { guid ->
                repository.getEpisode(guid)
                    ?: throw IllegalStateException("Episode not found for GUID $guid")
            }
        val updatedList =
            episodes.mapIndexed { index, episode ->
                episode.copy(favoriteTimestamp = timestamp - index)
            }
        repository.reorderFavorites(updatedList)
    }
}
