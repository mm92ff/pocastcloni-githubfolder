package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class MarkAllPodcastsSeenUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke() {
        repository.markAllAsSeen()
    }
}
