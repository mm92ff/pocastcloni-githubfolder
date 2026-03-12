package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@FlowPreview
class GetFavoriteEpisodesWithPodcastInfoUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider) {
    operator fun invoke(): Flow<List<EpisodeWithPodcastInfo>> {
        return podcastRepository.getFavoriteEpisodesWithPodcastInfoFlow()
            .map { map ->
                map.map { (episodeEntity, podcast) ->
                    EpisodeWithPodcastInfo(
                        episode = EpisodePresentation.from(episodeEntity),
                        podcast = podcast
                    )
                }
            }
            .distinctUntilChanged()
            .debounce(200L)
            .flowOn(dispatcherProvider.io)
    }
}
