package com.example.pocastcloni.data.cover

import com.example.pocastcloni.data.local.PodcastCoverStateDao
import com.example.pocastcloni.data.local.PodcastDao
import javax.inject.Inject
import javax.inject.Singleton

data class PodcastCoverRefreshSummary(
    val successfulCount: Int,
    val failureCount: Int
) {
    val totalCount: Int = successfulCount + failureCount
}

@Singleton
class PodcastCoverMaintenance
@Inject
@Suppress("LongParameterList")
constructor(
    private val podcastDao: PodcastDao,
    private val coverStateDao: PodcastCoverStateDao,
    private val thumbnailStore: PodcastCoverThumbnailStore,
    private val scheduler: PodcastCoverRefreshScheduler,
    private val materializer: PodcastCoverMaterializer,
    private val fileLifecycleLock: PodcastCoverFileLifecycleLock,
    private val clock: SystemPodcastCoverClock
) {
    suspend fun reconcileAndSchedule() {
        coverStateDao.seedMissingPodcastStates(clock.now())
        fileLifecycleLock.withLock {
            val states = coverStateDao.getAllStates()
            states.forEach { state ->
                val fileName = state.thumbnailFileName ?: return@forEach
                if (thumbnailStore.validFile(fileName) == null) {
                    coverStateDao.clearMissingFile(state.podcastRssUrl, fileName)
                }
            }
            val activeFiles =
                coverStateDao.getAllStates()
                    .mapNotNull { state -> state.thumbnailFileName }
                    .toSet()
            thumbnailStore.reconcile(activeFiles)
        }
        scheduler.enqueueAll()
    }

    suspend fun refreshAllNow(): PodcastCoverRefreshSummary {
        coverStateDao.seedMissingPodcastStates(clock.now())
        var successCount = 0
        var failureCount = 0
        podcastDao.getAllPodcastUrls().forEach { rssUrl ->
            when (materializer.materialize(rssUrl, force = true)) {
                is PodcastCoverMaterializationResult.Available -> successCount += 1
                else -> failureCount += 1
            }
        }
        return PodcastCoverRefreshSummary(successCount, failureCount)
    }
}
