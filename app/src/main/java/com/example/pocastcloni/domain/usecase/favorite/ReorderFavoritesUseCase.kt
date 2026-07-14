package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class ReorderFavoritesUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    suspend operator fun invoke(reorderedEpisodeIds: List<Long>) {
        podcastCommands.reorderFavorites(
            episodeIds = reorderedEpisodeIds,
            orderedAt = System.currentTimeMillis()
        )
    }
}
