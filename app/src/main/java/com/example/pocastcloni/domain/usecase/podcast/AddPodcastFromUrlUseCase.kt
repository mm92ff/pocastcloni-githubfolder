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
            // 1. Aktuelle Einstellungen laden
            val settings = userPreferencesRepository.userSettingsFlow.first()

            // 2. Podcast hinzufügen
            repository.addPodcast(
                url = url,
                downloadLimit = settings.autoDownloadLimit,
                mode = settings.feedUpdateMode,
                // FIX: forceFull auf false setzen.
                // Damit wird die User-Einstellung (settings.feedUpdateMode) nicht mehr überschrieben.
                // Ist "Smart Stream" aktiv, wird jetzt nur noch bis zum Limit (z.B. 3 Folgen) geladen und dann abgebrochen.
                forceFull = false
            )
        }
    }
}
