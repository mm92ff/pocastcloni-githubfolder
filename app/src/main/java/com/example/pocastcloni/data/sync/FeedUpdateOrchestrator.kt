package com.example.pocastcloni.data.sync

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateFailure
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.model.classifyFeedFailure
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedUpdateRunner
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedUpdateOrchestrator
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val feedSyncRunner: FeedSyncRunner,
    private val dispatcherProvider: DispatcherProvider
) : FeedUpdateRunner {
    private val updateSemaphore = Semaphore(MAX_FEED_UPDATE_FANOUT)

    override suspend fun updateAllPodcasts(
        downloadLimit: Int,
        mode: FeedUpdateMode,
        forceFull: Boolean,
        feedUrls: Set<String>?
    ): PodcastUpdateSummary = withContext(dispatcherProvider.io) {
        val request = FeedUpdateRequest(downloadLimit, mode, forceFull)
        val subscribedUrls = podcastQuery.getSubscribedUrls()
        val urls = feedUrls?.let { selected -> subscribedUrls.filter(selected::contains) } ?: subscribedUrls
        val outcomes =
            urls.chunked(MAX_FEED_UPDATE_FANOUT).flatMap { batch ->
                batch.map { url ->
                    async {
                        updatePodcast(url, request)
                    }
                }.awaitAll()
            }
        val failures = outcomes.filterNotNull()
        PodcastUpdateSummary(
            totalCount = urls.size,
            successfulCount = urls.size - failures.size,
            failureCount = failures.size,
            failures = failures
        )
    }

    private suspend fun updatePodcast(
        url: String,
        request: FeedUpdateRequest
    ): FeedUpdateFailure? = updateSemaphore.withPermit {
        val error =
            runCatching {
                feedSyncRunner.sync(
                    url = url,
                    downloadLimit = request.downloadLimit,
                    mode = request.mode,
                    forceFull = request.forceFull
                )
            }.exceptionOrNull() ?: return@withPermit null

        when (error) {
            is CancellationException -> throw error
            is Exception -> {
                Timber.w(error, "Failed to update a podcast feed")
                FeedUpdateFailure(url, classifyFeedFailure(error))
            }
            else -> throw error
        }
    }

    private companion object {
        const val MAX_FEED_UPDATE_FANOUT = 4
    }
}

private data class FeedUpdateRequest(
    val downloadLimit: Int,
    val mode: FeedUpdateMode,
    val forceFull: Boolean
)
