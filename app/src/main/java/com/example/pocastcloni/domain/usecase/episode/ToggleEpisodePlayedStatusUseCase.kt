package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ToggleEpisodePlayedStatusUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val podcastCommands: PodcastCommandPort,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(episodeId: Long) {
        withContext(dispatcherProvider.io) {
            val episode = podcastQuery.getEpisode(episodeId) ?: return@withContext
            podcastCommands.toggleEpisodePlayed(episode)
        }
    }
}
