package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshCoordinator
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import javax.inject.Inject

class FeedUpdateRunner
@Inject
constructor(
    private val refreshCoordinator: FeedRefreshCoordinator
) {
    suspend operator fun invoke(
        source: FeedRefreshSource = FeedRefreshSource.BACKGROUND
    ): PodcastUpdateSummary = refreshCoordinator.refresh(source)
}
