package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdatePodcastFeedUseCase
@Inject
constructor(
    private val feedSyncRunner: FeedSyncRunner,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        podcastUrl: String,
        forceFull: Boolean = false
    ) {
        val settings = userPreferencesRepository.userSettingsFlow.first()
        feedSyncRunner.sync(
            url = podcastUrl,
            downloadLimit = settings.autoDownloadLimit,
            mode = settings.feedUpdateMode,
            forceFull = forceFull
        )
    }
}
