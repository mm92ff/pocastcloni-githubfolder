package com.example.pocastcloni.domain.usecase.history

import com.example.pocastcloni.domain.repository.PodcastCommandPort
import javax.inject.Inject

class ClearHistoryUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort
) {
    suspend operator fun invoke() {
        podcastCommands.clearHistory()
    }
}
