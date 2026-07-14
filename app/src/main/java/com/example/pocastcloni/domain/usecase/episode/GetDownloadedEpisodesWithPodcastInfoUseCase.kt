package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@FlowPreview
class GetDownloadedEpisodesWithPodcastInfoUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val dispatcherProvider: DispatcherProvider
) {
    operator fun invoke(): Flow<List<EpisodeWithPodcastInfo>> {
        return podcastQuery.getDownloadedEpisodesWithPodcastFlow()
            .debounce(200L)
            .map { rows ->
                rows.map { row ->
                    EpisodeWithPodcastInfo(
                        episode = EpisodePresentation.from(row.episode),
                        podcast = row.podcast
                    )
                }
            }
            .flowOn(dispatcherProvider.io)
    }
}
