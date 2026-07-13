package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import javax.inject.Inject

class RefreshPodcastsUseCase
@Inject
constructor(
    private val refreshCoordinator: FeedRefreshCoordinator
) {
    suspend operator fun invoke(forceFull: Boolean = false): PodcastUpdateSummary {
        return refreshCoordinator.refresh(
            source = if (forceFull) FeedRefreshSource.MANUAL else FeedRefreshSource.STARTUP,
            forceFull = forceFull
        )
    }
}
