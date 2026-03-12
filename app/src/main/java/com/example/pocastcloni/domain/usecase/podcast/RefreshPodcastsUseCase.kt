package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RefreshPodcastsUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(forceFull: Boolean = false): PodcastUpdateSummary {
        val settings = userPreferencesRepository.userSettingsFlow.first()
        val effectiveForceFull = forceFull || settings.feedUpdateMode.requiresForceFullRefresh()

        return podcastRepository.updateAllPodcasts(
            downloadLimit = settings.autoDownloadLimit,
            mode = settings.feedUpdateMode,
            forceFull = effectiveForceFull
        )
    }
}
