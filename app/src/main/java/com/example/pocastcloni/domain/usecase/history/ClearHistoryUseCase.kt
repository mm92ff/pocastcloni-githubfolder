package com.example.pocastcloni.domain.usecase.history

import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke() {
        repository.clearHistory()
    }
}