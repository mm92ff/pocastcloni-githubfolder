package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetPlaybackHistoryWithPodcastInfoUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    operator fun invoke(): Flow<Map<EpisodeEntity, Podcast?>> {
        return podcastRepository.getPlaybackHistoryWithPodcastInfoFlow()
            .debounce(200L)
            .flowOn(dispatcherProvider.io)
    }
}
