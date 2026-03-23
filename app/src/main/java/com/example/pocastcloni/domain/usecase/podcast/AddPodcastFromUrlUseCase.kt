package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AddPodcastFromUrlUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(url: String) {
        withContext(dispatcherProvider.io) {
            // 1. Load current settings
            val settings = userPreferencesRepository.userSettingsFlow.first()

            // 2. Add the podcast
            repository.addPodcast(
                url = url,
                downloadLimit = settings.autoDownloadLimit,
                mode = settings.feedUpdateMode,
                // FIX: set forceFull to false so the user's feedUpdateMode setting is respected.
                // When "Smart Stream" is active, only up to the limit (e.g. 3 episodes) will be fetched.
                forceFull = false
            )
        }
    }
}
