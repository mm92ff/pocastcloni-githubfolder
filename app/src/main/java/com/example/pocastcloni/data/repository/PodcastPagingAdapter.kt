package com.example.pocastcloni.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

internal class PodcastPagingAdapter(
    private val podcastDao: PodcastDao,
    private val dispatcherProvider: DispatcherProvider
) : PodcastPagingSource {
    override fun getEpisodesPagedFlow(rssUrl: String): Flow<PagingData<EpisodeEntity>> =
        Pager(
            config =
            PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                initialLoadSize = INITIAL_LOAD_SIZE
            ),
            pagingSourceFactory = { podcastDao.getEpisodesPagingSource(rssUrl) }
        ).flow.flowOn(dispatcherProvider.io)

    private companion object {
        const val PAGE_SIZE = 20
        const val INITIAL_LOAD_SIZE = 40
    }
}
