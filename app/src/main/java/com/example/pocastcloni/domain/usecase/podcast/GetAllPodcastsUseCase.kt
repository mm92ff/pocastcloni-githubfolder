package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllPodcastsUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    operator fun invoke(): Flow<List<Podcast>> {
        return repository.getAllPodcastsFlow()
    }
}