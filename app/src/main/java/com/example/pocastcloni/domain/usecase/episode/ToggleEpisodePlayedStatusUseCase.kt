package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ToggleEpisodePlayedStatusUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(episodeId: Long) {
        withContext(dispatcherProvider.io) {
            val episode = repository.getEpisode(episodeId) ?: return@withContext
            repository.toggleEpisodePlayed(episode)
        }
    }
}
