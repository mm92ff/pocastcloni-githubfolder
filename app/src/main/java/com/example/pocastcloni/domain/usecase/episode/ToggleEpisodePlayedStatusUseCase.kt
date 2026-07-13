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

            // 1. Toggle the played status
            repository.toggleEpisodePlayed(episode)

            // 2. Recalculate the podcast status based on the LATEST episode
            updatePodcastStatusBasedOnLatest(episode.podcastRssUrl)
        }
    }

    private suspend fun updatePodcastStatusBasedOnLatest(podcastUrl: String) {
        // firstOrNull() is the latest episode (due to DAO sort order)
        val latestEpisode = repository.getEpisodesForSync(podcastUrl).firstOrNull()

        // Show dot = latest episode is unplayed
        val showDot = latestEpisode != null && !latestEpisode.isPlayed

        val podcast = repository.getPodcastEntityByUrl(podcastUrl)
        if (podcast != null && podcast.hasNewEpisodes != showDot) {
            repository.updatePodcastEntity(podcast.copy(hasNewEpisodes = showDot))
        }
    }
}
