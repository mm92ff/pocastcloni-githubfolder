package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class MarkEpisodePlayedUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(guid: String) {
        withContext(dispatcherProvider.io) {
            // 1. Mark the episode as played
            repository.markEpisodePlayed(guid, true, Date())

            // 2. Check the status of the latest episode
            val episode = repository.getEpisode(guid) ?: return@withContext
            updatePodcastStatusBasedOnLatest(episode.podcastRssUrl)
        }
    }

    private suspend fun updatePodcastStatusBasedOnLatest(podcastUrl: String) {
        // Fetch the list (sorted by date descending, as in SyncUseCase)
        // firstOrNull() is therefore the most recent episode.
        val latestEpisode = repository.getEpisodesForSync(podcastUrl).firstOrNull()

        // The dot should only be shown when the latest episode exists AND has not been played yet.
        val showDot = latestEpisode != null && !latestEpisode.isPlayed

        val podcast = repository.getPodcastEntityByUrl(podcastUrl)
        // Only update if the status actually changes
        if (podcast != null && podcast.hasNewEpisodes != showDot) {
            repository.updatePodcastEntity(podcast.copy(hasNewEpisodes = showDot))
        }
    }
}
