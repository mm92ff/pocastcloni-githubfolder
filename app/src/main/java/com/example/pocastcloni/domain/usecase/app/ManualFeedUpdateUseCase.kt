package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshCoordinator
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import com.example.pocastcloni.util.Constants
import javax.inject.Inject

class ManualFeedUpdateUseCase
@Inject
constructor(
    private val refreshCoordinator: FeedRefreshCoordinator
) {
    suspend operator fun invoke(): PodcastUpdateSummary =
        refreshCoordinator.refresh(
            source = FeedRefreshSource.MANUAL,
            forceFull = true,
            downloadLimitOverride = Constants.Preferences.NO_DOWNLOAD_LIMIT
        )
}
