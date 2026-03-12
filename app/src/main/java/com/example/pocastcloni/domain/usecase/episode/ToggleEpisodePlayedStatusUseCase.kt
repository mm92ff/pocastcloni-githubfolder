package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ToggleEpisodePlayedStatusUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(guid: String) {
        withContext(dispatcherProvider.io) {
            val episode = repository.getEpisode(guid) ?: return@withContext

            // 1. Status umschalten
            repository.toggleEpisodePlayed(episode)

            // 2. Podcast-Status basierend auf der NEUSTEN Episode neu berechnen
            updatePodcastStatusBasedOnLatest(episode.podcastRssUrl)
        }
    }

    private suspend fun updatePodcastStatusBasedOnLatest(podcastUrl: String) {
        // firstOrNull() ist die neuste Episode (dank Sortierung im DAO)
        val latestEpisode = repository.getEpisodesForSync(podcastUrl).firstOrNull()

        // Dot anzeigen = Neuste Episode ist ungespielt
        val showDot = latestEpisode != null && !latestEpisode.isPlayed

        val podcast = repository.getPodcastEntityByUrl(podcastUrl)
        if (podcast != null && podcast.hasNewEpisodes != showDot) {
            repository.updatePodcastEntity(podcast.copy(hasNewEpisodes = showDot))
        }
    }
}