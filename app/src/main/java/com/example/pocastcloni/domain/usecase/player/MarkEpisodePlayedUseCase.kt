package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class MarkEpisodePlayedUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(guid: String) {
        withContext(dispatcherProvider.io) {
            // 1. Episode als gespielt markieren
            repository.markEpisodePlayed(guid, true, Date())

            // 2. Status der NEUSTEN Episode prüfen
            val episode = repository.getEpisode(guid) ?: return@withContext
            updatePodcastStatusBasedOnLatest(episode.podcastRssUrl)
        }
    }

    private suspend fun updatePodcastStatusBasedOnLatest(podcastUrl: String) {
        // Hole die Liste (sortiert nach Datum absteigend, wie im SyncUseCase)
        // firstOrNull() ist somit die allerneueste Episode.
        val latestEpisode = repository.getEpisodesForSync(podcastUrl).firstOrNull()

        // Der Dot soll nur angezeigt werden, wenn die neuste Episode existiert UND noch NICHT gespielt ist.
        val showDot = latestEpisode != null && !latestEpisode.isPlayed

        val podcast = repository.getPodcastEntityByUrl(podcastUrl)
        // Nur updaten, wenn sich der Status wirklich ändert
        if (podcast != null && podcast.hasNewEpisodes != showDot) {
            repository.updatePodcastEntity(podcast.copy(hasNewEpisodes = showDot))
        }
    }
}