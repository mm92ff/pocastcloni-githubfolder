package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ReorderFavoritesUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(reorderedEpisodeIds: List<Long>) {
        repository.reorderFavorites(
            episodeIds = reorderedEpisodeIds,
            orderedAt = System.currentTimeMillis()
        )
    }
}
