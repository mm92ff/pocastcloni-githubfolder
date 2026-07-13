package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FeedUpdateRunner
@Inject
constructor(
    private val repository: PodcastRepository,
    private val preferences: UserPreferencesRepository
) {
    suspend operator fun invoke(): PodcastUpdateSummary {
        val settings = preferences.userSettingsFlow.first()
        return repository.updateAllPodcasts(
            downloadLimit = settings.autoDownloadLimit,
            mode = settings.feedUpdateMode,
            forceFull = settings.feedUpdateMode.requiresForceFullRefresh()
        )
    }
}
