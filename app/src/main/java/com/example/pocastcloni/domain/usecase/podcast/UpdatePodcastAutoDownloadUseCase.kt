package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class UpdatePodcastAutoDownloadUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(podcastUrl: String, enabled: Boolean) {
        withContext(dispatcherProvider.io) {
            val podcast = repository.getPodcast(podcastUrl)
            if (podcast == null) {
                Timber.w("Tried to update auto-download for missing podcast: %s", podcastUrl)
                return@withContext
            }
            repository.updatePodcastSettings(podcast, enabled)
        }
    }
}
