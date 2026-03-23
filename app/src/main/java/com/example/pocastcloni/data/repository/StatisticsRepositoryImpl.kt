package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.repository.AppStatistics
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// DataStore Definition
private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Statistics.DATASTORE_NAME)

@Singleton
class StatisticsRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val podcastDao: PodcastDao,
    @ApplicationScope private val appScope: CoroutineScope
) : StatisticsRepository {
    private object Keys {
        val DOWNLOAD_WIFI = longPreferencesKey(Constants.Statistics.KEY_DOWNLOAD_WIFI)
        val DOWNLOAD_MOBILE = longPreferencesKey(Constants.Statistics.KEY_DOWNLOAD_MOBILE)
        val STREAM_WIFI = longPreferencesKey(Constants.Statistics.KEY_STREAM_WIFI)
        val STREAM_MOBILE = longPreferencesKey(Constants.Statistics.KEY_STREAM_MOBILE)
        val UPLOAD = longPreferencesKey(Constants.Statistics.KEY_UPLOAD)
        val LISTENING_TIME = longPreferencesKey(Constants.Statistics.KEY_LISTENING_TIME)
        val STATISTICS_STARTED_AT = longPreferencesKey(Constants.Statistics.KEY_STATISTICS_STARTED_AT)
    }

    init {
        // Sets the start timestamp once on the first app launch (singleton)
        appScope.launch {
            context.statsDataStore.edit { prefs ->
                if (prefs[Keys.STATISTICS_STARTED_AT] == null) {
                    prefs[Keys.STATISTICS_STARTED_AT] = System.currentTimeMillis()
                }
            }
        }
    }

    // --- BUFFER FOR OPTIMIZATION (prevent write amplification) ---

    // 1. Listening time buffer (writes only every 10 seconds)
    private val pendingListeningTimeMs = AtomicLong(0)
    private val TIME_FLUSH_THRESHOLD_MS = 10_000L

    // 2. Streaming buffer (writes only every 1024 KB)
    private val pendingStreamWifiBytes = AtomicLong(0)
    private val pendingStreamMobileBytes = AtomicLong(0)
    private val STREAM_FLUSH_THRESHOLD_BYTES = 1024 * 1024L // 1024 KB

    // --- DATA FLOW ---

    private val dataStoreStats: Flow<AppStatistics> =
        context.statsDataStore.data.map { prefs ->
            AppStatistics(
                downloadWifiBytes = prefs[Keys.DOWNLOAD_WIFI] ?: 0L,
                downloadMobileBytes = prefs[Keys.DOWNLOAD_MOBILE] ?: 0L,
                streamWifiBytes = prefs[Keys.STREAM_WIFI] ?: 0L,
                streamMobileBytes = prefs[Keys.STREAM_MOBILE] ?: 0L,
                uploadBytes = prefs[Keys.UPLOAD] ?: 0L,
                totalListeningTimeMs = prefs[Keys.LISTENING_TIME] ?: 0L,
                statisticsStartedAt = prefs[Keys.STATISTICS_STARTED_AT] ?: 0L
            )
        }

    override val statsFlow: Flow<AppStatistics> =
        combine(
            dataStoreStats,
            podcastDao.getTotalEpisodeCount(),
            podcastDao.getEpisodesInProgressCount(),
            podcastDao.getPlayedEpisodesCount()
        ) { dsStats, total, inProgress, played ->
            dsStats.copy(
                totalEpisodes = total,
                episodesInProgress = inProgress,
                episodesPlayed = played
            )
        }

    // --- ACTIONS ---

    override suspend fun addDownloadBytes(
        bytes: Long,
        isWifi: Boolean
    ) {
        // Downloads usually come in blocks or at the end of the worker,
        // so aggressive buffering is not needed here.
        context.statsDataStore.edit { prefs ->
            val key = if (isWifi) Keys.DOWNLOAD_WIFI else Keys.DOWNLOAD_MOBILE
            val current = prefs[key] ?: 0L
            prefs[key] = current + bytes
        }
    }

    override suspend fun addStreamBytes(
        bytes: Long,
        isWifi: Boolean
    ) {
        // NEW: Buffer logic for streaming
        // 1. Add bytes in RAM only
        val buffer = if (isWifi) pendingStreamWifiBytes else pendingStreamMobileBytes
        val currentPending = buffer.addAndGet(bytes)

        // 2. Check whether enough has been accumulated (512 KB)
        if (currentPending >= STREAM_FLUSH_THRESHOLD_BYTES) {
            val bytesToWrite = buffer.getAndSet(0)

            // Write only if there is actually something (thread-safety check)
            if (bytesToWrite > 0) {
                // 3. Write to flash storage
                context.statsDataStore.edit { prefs ->
                    val key = if (isWifi) Keys.STREAM_WIFI else Keys.STREAM_MOBILE
                    val current = prefs[key] ?: 0L
                    prefs[key] = current + bytesToWrite
                }
            }
        }
    }

    override suspend fun addUploadBytes(bytes: Long) {
        context.statsDataStore.edit { prefs ->
            val current = prefs[Keys.UPLOAD] ?: 0L
            prefs[Keys.UPLOAD] = current + bytes
        }
    }

    override suspend fun addListeningTime(ms: Long) {
        // Existing logic: buffer for time
        val currentPending = pendingListeningTimeMs.addAndGet(ms)

        if (currentPending >= TIME_FLUSH_THRESHOLD_MS) {
            val toWrite = pendingListeningTimeMs.getAndSet(0)
            if (toWrite > 0) {
                context.statsDataStore.edit { prefs ->
                    val current = prefs[Keys.LISTENING_TIME] ?: 0L
                    prefs[Keys.LISTENING_TIME] = current + toWrite
                }
            }
        }
    }

    override suspend fun resetStatistics() {
        // 1. Reset all RAM buffers!
        pendingListeningTimeMs.set(0)
        pendingStreamWifiBytes.set(0)
        pendingStreamMobileBytes.set(0)

        // 2. Clear DataStore and reset start timestamp to now
        context.statsDataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.STATISTICS_STARTED_AT] = System.currentTimeMillis()
        }

        // 3. Clean up DB history
        podcastDao.clearHistory()
    }
}
