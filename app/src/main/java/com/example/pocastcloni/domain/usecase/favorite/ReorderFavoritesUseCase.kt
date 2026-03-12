package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ReorderFavoritesUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(reorderedList: List<EpisodeEntity>) {
        val timestamp = System.currentTimeMillis()
        val updatedList = reorderedList.mapIndexed { index, episode ->
            episode.copy(favoriteTimestamp = timestamp - index)
        }
        repository.reorderFavorites(updatedList)
    }
}