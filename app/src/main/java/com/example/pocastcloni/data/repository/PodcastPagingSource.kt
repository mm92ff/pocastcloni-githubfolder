package com.example.pocastcloni.data.repository

import androidx.paging.PagingData
import com.example.pocastcloni.data.local.EpisodeEntity
import kotlinx.coroutines.flow.Flow

interface PodcastPagingSource {
    fun getEpisodesPagedFlow(rssUrl: String): Flow<PagingData<EpisodeEntity>>
}
