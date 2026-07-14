package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class MarkAllPodcastsSeenUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    suspend operator fun invoke() {
        podcastCommands.markAllAsSeen()
    }
}
