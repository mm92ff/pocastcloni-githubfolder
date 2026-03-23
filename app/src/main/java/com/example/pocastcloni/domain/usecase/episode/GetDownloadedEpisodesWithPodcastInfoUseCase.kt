package com.example.pocastcloni.domain.usecase.episode

// TODO: ARCHITECTURE BOUNDARY VIOLATION - Domain layer importing data mapper functions
// FIXME: Domain use cases should not depend on data layer implementation details.
// This mapper function should be moved to domain or accessed via repository interface.
import com.example.pocastcloni.data.repository.toPodcastDomain
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetDownloadedEpisodesWithPodcastInfoUseCase
@Inject
constructor(
    private val podcastRepository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    operator fun invoke(): Flow<List<EpisodeWithPodcastInfo>> {
        return podcastRepository.getDownloadedEpisodesWithPodcastLiteFlow()
            .debounce(200L)
            .map { rows ->
                rows.map { row ->
                    EpisodeWithPodcastInfo(
                        episode = EpisodePresentation.from(row.episode),
                        podcast = row.toPodcastDomain()
                    )
                }
            }
            .flowOn(dispatcherProvider.io)
    }
}
