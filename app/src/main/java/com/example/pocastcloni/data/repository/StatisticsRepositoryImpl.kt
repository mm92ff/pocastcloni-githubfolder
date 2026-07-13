package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.repository.AppStatistics
import com.example.pocastcloni.domain.repository.StatisticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepositoryImpl
@Inject
constructor(
    private val dataStore: StatisticsDataStore,
    private val podcastDao: PodcastDao,
    private val streamingRecorder: StreamingStatisticsRecorder,
    @ApplicationScope appScope: CoroutineScope
) : StatisticsRepository {
    init {
        appScope.launch {
            dataStore.initializeStatisticsStartedAt()
        }
    }

    override val statsFlow: Flow<AppStatistics> =
        combine(
            dataStore.statsFlow,
            podcastDao.getTotalEpisodeCount(),
            podcastDao.getEpisodesInProgressCount(),
            podcastDao.getPlayedEpisodesCount()
        ) { storedStats, total, inProgress, played ->
            storedStats.copy(
                totalEpisodes = total,
                episodesInProgress = inProgress,
                episodesPlayed = played
            )
        }

    override suspend fun addDownloadBytes(
        bytes: Long,
        isWifi: Boolean
    ) {
        dataStore.addDownloadBytes(bytes, isWifi)
    }

    override suspend fun addUploadBytes(bytes: Long) {
        dataStore.addUploadBytes(bytes)
    }

    override suspend fun addListeningTime(ms: Long) {
        if (ms <= 0L) return
        // PlaybackAnalyticsHandler already batches regular writes for one minute and flushes lifecycle events.
        dataStore.addListeningTime(ms)
    }

    override suspend fun resetStatistics() {
        streamingRecorder.resetStatistics {
            dataStore.resetStatistics()
        }
        podcastDao.clearHistory()
    }
}
