package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class ReorderPodcastsUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    /**
     * Persists a complete podcast order expressed only by RSS URL. The caller owns membership and
     * uniqueness validation; the command adapter assigns contiguous sort indices transactionally.
     */
    suspend operator fun invoke(rssUrlsInOrder: List<String>) {
        podcastCommands.reorderPodcasts(rssUrlsInOrder = rssUrlsInOrder)
    }
}
