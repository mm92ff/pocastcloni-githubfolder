package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdatePodcastFeedUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        podcastUrl: String,
        forceFull: Boolean = false
    ) {
        val settings = userPreferencesRepository.userSettingsFlow.first()
        repository.addPodcast(
            url = podcastUrl,
            downloadLimit = settings.autoDownloadLimit,
            mode = settings.feedUpdateMode,
            forceFull = forceFull
        )
    }
}
