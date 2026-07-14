package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class ToggleFavoriteEpisodeUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    suspend operator fun invoke(
        episodeId: Long,
        isFavorite: Boolean
    ) {
        val newFavoriteState = !isFavorite
        val timestamp = if (newFavoriteState) System.currentTimeMillis() else null
        podcastCommands.setFavoriteStatus(episodeId, newFavoriteState, timestamp)
    }
}
