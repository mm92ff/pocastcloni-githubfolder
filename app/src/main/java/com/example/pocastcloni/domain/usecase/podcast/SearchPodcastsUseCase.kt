package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.PodcastSearchResult
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import javax.inject.Inject

class SearchPodcastsUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort
) {
    suspend operator fun invoke(query: String): List<PodcastSearchResult> {
        return podcastQuery.searchPodcasts(query)
    }
}
