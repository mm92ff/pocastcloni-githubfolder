package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllPodcastsUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort
) {
    operator fun invoke(): Flow<List<Podcast>> {
        return podcastQuery.getAllPodcastsFlow()
    }
}
