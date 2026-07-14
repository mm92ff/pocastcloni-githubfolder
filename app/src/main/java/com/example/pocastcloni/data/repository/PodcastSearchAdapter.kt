package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.PodcastSearchResult
import com.example.pocastcloni.domain.repository.PodcastSearchPort
import com.example.pocastcloni.util.RetryingDataFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class PodcastSearchAdapter(
    private val podcastDao: PodcastDao,
    private val itunesSearchApi: ItunesSearchApi,
    private val dispatcherProvider: DispatcherProvider
) : PodcastSearchPort {
    override fun searchEpisodesFlow(query: String): Flow<List<Episode>> {
        if (query.isBlank()) return flowOf(emptyList())
        return RetryingDataFlow.bounded(
            podcastDao.searchEpisodes("*$query*").map { episodes ->
                episodes.map(EpisodeEntity::toDomain)
            }
        ).flowOn(dispatcherProvider.io)
    }

    override suspend fun searchPodcasts(term: String): List<PodcastSearchResult> =
        withContext(dispatcherProvider.io) {
            itunesSearchApi.searchPodcasts(term).results.map { it.toDomain() }
        }
}
