package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.remote.ItunesPodcastDto
import com.example.pocastcloni.domain.repository.PodcastRepository
import javax.inject.Inject

class SearchPodcastsUseCase
@Inject
constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(query: String): List<ItunesPodcastDto> {
        return repository.searchPodcasts(query)
    }
}
