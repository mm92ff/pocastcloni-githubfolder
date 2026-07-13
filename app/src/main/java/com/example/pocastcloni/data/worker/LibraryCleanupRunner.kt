package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class LibraryCleanupRunner
@Inject
constructor(
    private val podcastRepository: PodcastRepository,
    private val preferences: UserPreferencesRepository
) {
    suspend operator fun invoke(): Boolean {
        val settings = preferences.userSettingsFlow.first()
        if (!settings.autoCleanupEnabled) return false

        podcastRepository.pruneLibrary(settings.cleanupKeepLimit)
        return true
    }
}
