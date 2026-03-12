package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.home.detail.toEpisodeUiModel
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetDownloadedEpisodesWithPodcastInfoUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    operator fun invoke(): Flow<List<EpisodeUiModel>> {
        return podcastRepository.getDownloadedEpisodesWithPodcastLiteFlow()
            .debounce(200L)
            .map { rows ->
                rows.map { row ->
                    val podcastTitle = row.podcast?.title ?: Constants.UNKNOWN_PODCAST
                    val podcastImageUrl = row.podcast?.imageUrl ?: ""

                    row.episode.toEpisodeUiModel(
                        podcastName = podcastTitle,
                        podcastImageUrl = podcastImageUrl,
                        downloadProgress = 1.0f // Downloaded episodes are always at 100%
                    )
                }
            }
            .flowOn(dispatcherProvider.io)
    }
}
