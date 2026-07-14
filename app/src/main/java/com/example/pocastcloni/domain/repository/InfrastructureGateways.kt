package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.domain.model.Episode
import kotlinx.coroutines.flow.StateFlow

interface EpisodeDownloadScheduler {
    val downloadProgressFlow: StateFlow<Map<Long, Float>>

    suspend fun queue(episode: Episode)

    suspend fun cancelAndDelete(episode: Episode)
}

interface PodcastRemovalGateway {
    suspend fun cancelActiveDownloads(episodes: List<Episode>)

    suspend fun deleteDownloadedFiles(episodes: List<Episode>)
}

interface LocalNetworkApprovalPort {
    fun approveFeed(feedUrl: String)
}

interface AppResetGateway {
    suspend fun runResetAndReconcile(
        clearSettings: suspend () -> Unit,
        resetDatabase: suspend () -> Unit,
        loadFinalSettings: suspend () -> UserSettings,
        markPending: Boolean
    )

    suspend fun isPending(): Boolean
}
