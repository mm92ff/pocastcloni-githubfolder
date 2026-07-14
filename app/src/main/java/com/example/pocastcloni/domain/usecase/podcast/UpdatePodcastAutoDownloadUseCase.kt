package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class UpdatePodcastAutoDownloadUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val podcastCommands: PodcastCommandPort,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(
        podcastUrl: String,
        enabled: Boolean
    ) {
        withContext(dispatcherProvider.io) {
            val podcast = podcastQuery.getPodcast(podcastUrl)
            if (podcast == null) {
                Timber.w("Tried to update auto-download for missing podcast: %s", podcastUrl)
                return@withContext
            }
            podcastCommands.updatePodcastSettings(podcast, enabled)
        }
    }
}
