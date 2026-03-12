package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ManualFeedUpdateUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(): PodcastUpdateSummary {
        return withContext(dispatcherProvider.io) {
            repository.updateAllPodcasts(
                Constants.Preferences.NO_DOWNLOAD_LIMIT,
                FeedUpdateMode.ALWAYS_FULL,
                forceFull = true
            )
        }
    }
}
