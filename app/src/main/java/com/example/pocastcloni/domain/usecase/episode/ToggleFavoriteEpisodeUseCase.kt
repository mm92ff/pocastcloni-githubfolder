package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ToggleFavoriteEpisodeUseCase
@Inject
constructor(
    private val podcastRepository: PodcastRepository
) {
    suspend operator fun invoke(
        guid: String,
        isFavorite: Boolean
    ) {
        val newFavoriteState = !isFavorite
        val timestamp = if (newFavoriteState) System.currentTimeMillis() else null
        podcastRepository.setFavoriteStatus(guid, newFavoriteState, timestamp)
    }
}
