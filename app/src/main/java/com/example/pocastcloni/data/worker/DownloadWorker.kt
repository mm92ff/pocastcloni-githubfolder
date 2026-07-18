package com.example.pocastcloni.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.ConnectivityProvider
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.requireApprovedPodcastResource
import com.example.pocastcloni.util.shouldUseLocalNetworkForResource
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Named

private const val DOWNLOAD_NOTIFICATION_ID_BASE = 2_000
private const val DOWNLOAD_NOTIFICATION_ID_MASK = 0x3FFF

/**
 * Owns one resumable transfer and its attempt-scoped publication.
 *
 * Network bytes remain in episode staging until the transfer completes. The short prepare,
 * database compare-and-set, and visibility transition runs under [DownloadPublicationGate], which
 * lets startup recovery observe only stable publication boundaries without serializing queue or
 * cancellation bookkeeping. Failed and cancelled attempts retain staging only when retry ownership
 * is confirmed; committed targets are recovered from their database path after process death.
 */
@HiltWorker
@Suppress("LongParameterList")
class DownloadWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val podcastQuery: PodcastQueryPort,
    private val podcastCommands: PodcastCommandPort,
    private val statsRepo: StatisticsRepository,
    private val connectivityProvider: ConnectivityProvider,
    private val okHttpClient: OkHttpClient,
    @Named("LocalNetworkClient") private val localNetworkClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val objectMapper: ObjectMapper,
    private val workManager: WorkManager
) : CoroutineWorker(context, params) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val episode = resolveEpisode() ?: return Result.failure()
        val episodeId = episode.episodeId
        val stagingFiles = downloadStagingFiles(applicationContext.filesDir, episodeId)
        var publication: PendingDownloadPublication? = null
        var committed = false
        var retainPartialForRetry = false

        try {
            val podcast = podcastQuery.getPodcast(episode.podcastRssUrl)
            val url = episode.enclosureUrl
            requireApprovedPodcastResource(
                feedUrl = episode.podcastRssUrl,
                resourceUrl = url,
                allowInsecureHttp = podcast?.allowInsecureHttp == true,
                allowLocalNetwork = podcast?.allowLocalNetwork == true
            )
            val started =
                podcastCommands.compareAndSetDownloadStatus(
                    episodeId = episodeId,
                    expectedStatuses = listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING),
                    status = DownloadStatus.DOWNLOADING,
                    path = null
                )
            if (!started) return Result.failure()

            val localizedContext = ContextCompat.getContextForLanguage(context)
            val podcastTitle =
                podcast?.title?.ifBlank { null }
                    ?: localizedContext.getString(R.string.unknown_podcast_title)
            val episodeTitle = episode.title
            val mimeType = episode.type.ifBlank { DEFAULT_MIME_TYPE }
            val fileName =
                createDownloadFileName(
                    podcastTitle = podcastTitle,
                    episodeTitle =
                    episodeTitle.ifBlank {
                        localizedContext.getString(R.string.no_episode_title)
                    },
                    mimeType = mimeType,
                    sourceUrl = url,
                    episodeId = episodeId
                )
            val progressReporter = DownloadProgressReporter()
            setForeground(createForegroundInfo(episodeTitle, null))
            setProgress(workDataOf(PROGRESS_KEY to 0f))

            val saveToPublicDownloads = loadPublicDownloadPreference()
            val baseClient =
                if (
                    shouldUseLocalNetworkForResource(
                        episode.podcastRssUrl,
                        url,
                        podcast?.allowLocalNetwork == true
                    )
                ) {
                    localNetworkClient
                } else {
                    okHttpClient
                }
            val downloadClient =
                baseClient.newBuilder()
                    .callTimeout(Constants.Network.DOWNLOAD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .build()
            val resumableDownload =
                ResumableDownload(
                    client = downloadClient,
                    metadataStore = DownloadResumeMetadataStore(objectMapper),
                    maxBytes = Constants.SecurityLimits.MAX_DOWNLOAD_BYTES,
                    storageReserveBytes = Constants.SecurityLimits.MIN_FREE_STORAGE_RESERVE_BYTES,
                    storageRecheckIntervalBytes = Constants.SecurityLimits.STORAGE_RECHECK_INTERVAL_BYTES
                )
            val transfer =
                resumableDownload.download(
                    url = url,
                    stagingFiles = stagingFiles,
                    availableBytes = { stagingFiles.partFile.parentFile?.usableSpace ?: 0L }
                ) { downloadedBytes, totalBytes ->
                    progressReporter.report(downloadedBytes, totalBytes)?.let { percent ->
                        setProgress(workDataOf(PROGRESS_KEY to (percent / 100f)))
                        setForeground(createForegroundInfo(episodeTitle, percent))
                    }
                }

            DownloadPublicationGate.withLock {
                val preparedPublication =
                    DownloadPublisher(applicationContext).prepare(
                        stagedFile = transfer.partFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        saveToPublicDownloads = saveToPublicDownloads,
                        attemptId = id
                    )
                publication = preparedPublication
                commitDownloadPublication(
                    publication = preparedPublication,
                    commitDatabase = { path ->
                        podcastCommands.compareAndSetDownloadStatus(
                            episodeId = episodeId,
                            expectedStatuses = listOf(DownloadStatus.DOWNLOADING),
                            status = DownloadStatus.DOWNLOADED,
                            path = path
                        )
                    },
                    compensateDatabase = { path ->
                        podcastCommands.compareAndSetDownloadStatusAndPath(
                            episodeId = episodeId,
                            expectedStatus = DownloadStatus.DOWNLOADED,
                            expectedPath = path,
                            status = DownloadStatus.FAILED,
                            path = null
                        )
                    }
                )
                committed = true
            }
            val committedPublication = requireNotNull(publication)
            stagingFiles.delete()
            setProgress(workDataOf(PROGRESS_KEY to 1f))
            setForeground(createForegroundInfo(episodeTitle, 100))
            recordStatisticsBestEffort(committedPublication.totalBytes)
            return Result.success(workDataOf(Constants.DOWNLOAD_WORKER_OUTPUT_PATH to committedPublication.path))
        } catch (error: CancellationException) {
            Timber.i("Download cancelled for episode %d", episodeId)
            retainPartialForRetry =
                handleDownloadWorkerCancellation(
                    workManager = workManager,
                    workId = id,
                    episodeId = episodeId,
                    query = podcastQuery,
                    commands = podcastCommands,
                    stagingFiles = stagingFiles,
                    publication = publication
                )
            if (!retainPartialForRetry) publication = null
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Download failed for episode %d", episodeId)
            val shouldRetry = shouldRetryDownloadFailure(error, runAttemptCount, MAX_RETRY_ATTEMPTS)
            if (shouldRetry) {
                val transitionedToQueued =
                    podcastCommands.compareAndSetDownloadStatus(
                        episodeId = episodeId,
                        expectedStatuses = listOf(DownloadStatus.DOWNLOADING),
                        status = DownloadStatus.QUEUED,
                        path = null
                    )
                retainPartialForRetry =
                    transitionedToQueued && publication == null && stagingFiles.partFile.isFile
                return if (transitionedToQueued) Result.retry() else Result.failure()
            }
            podcastCommands.compareAndSetDownloadStatus(
                episodeId = episodeId,
                expectedStatuses = listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING),
                status = DownloadStatus.FAILED,
                path = null
            )
            return Result.failure()
        } finally {
            if (!committed && !retainPartialForRetry) {
                runCatching { publication?.cleanup() }
                stagingFiles.delete()
            }
        }
    }

    private suspend fun resolveEpisode() =
        inputData.getLong(Constants.DOWNLOAD_WORKER_EPISODE_ID, 0L)
            .takeIf { it > 0L }
            ?.let { podcastQuery.getEpisode(it) }
            ?: inputData.getString(Constants.DOWNLOAD_WORKER_LEGACY_GUID)
                ?.let { podcastQuery.resolveLegacyDownloadEpisode(it) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadPublicDownloadPreference(): Boolean =
        try {
            userPreferencesRepository.userSettingsFlow.first().saveToDownloadsFolder
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Could not read download location setting, using private storage")
            false
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun recordStatisticsBestEffort(totalBytes: Long) {
        if (totalBytes <= 0L) return
        try {
            statsRepo.addDownloadBytes(totalBytes, connectivityProvider.wifiStatus.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Could not persist committed download statistics")
        }
    }

    private fun createForegroundInfo(
        episodeTitle: String,
        progressPercent: Int?
    ): ForegroundInfo =
        createDownloadForegroundInfo(
            context = context,
            workManager = workManager,
            workId = id,
            episodeTitle = episodeTitle,
            progressPercent = progressPercent
        )

    private companion object {
        const val PROGRESS_KEY = "progress"
        const val MAX_RETRY_ATTEMPTS = 3
        const val DEFAULT_MIME_TYPE = "audio/mpeg"
    }
}

internal fun createDownloadForegroundInfo(
    context: Context,
    workManager: WorkManager,
    workId: UUID,
    episodeTitle: String,
    progressPercent: Int?
): ForegroundInfo {
    val localizedContext = ContextCompat.getContextForLanguage(context)
    val localizedEpisodeTitle =
        episodeTitle.ifBlank { localizedContext.getString(R.string.no_episode_title) }
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                localizedContext.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
    val progressText =
        progressPercent?.let { localizedContext.getString(R.string.download_notification_progress, it) }
            ?: localizedContext.getString(R.string.download_notification_starting)
    val notification: Notification =
        NotificationCompat.Builder(localizedContext, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(localizedContext.getString(R.string.download_notification_title))
            .setContentText(
                localizedContext.getString(
                    R.string.download_notification_content,
                    localizedEpisodeTitle,
                    progressText
                )
            )
            .setProgress(PROGRESS_PERCENT_MAX, progressPercent ?: 0, progressPercent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                localizedContext.getString(R.string.cancel),
                workManager.createCancelPendingIntent(workId)
            )
            .build()
    val notificationId = DOWNLOAD_NOTIFICATION_ID_BASE + (workId.hashCode() and DOWNLOAD_NOTIFICATION_ID_MASK)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    } else {
        ForegroundInfo(notificationId, notification)
    }
}

internal class DownloadProgressReporter(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private var lastReportAt = nowMillis()
    private var lastPercent = 0

    fun report(
        downloadedBytes: Long,
        totalBytes: Long?
    ): Int? {
        if (totalBytes == null || totalBytes <= 0L) return null
        val percent =
            ((downloadedBytes * PROGRESS_PERCENT_MAX) / totalBytes)
                .toInt()
                .coerceIn(0, PROGRESS_PERCENT_MAX - 1)
        val now = nowMillis()
        val enoughTimePassed = now - lastReportAt >= PROGRESS_MIN_INTERVAL_MS
        val enoughProgress = percent - lastPercent >= PROGRESS_MIN_PERCENTAGE_POINTS
        return if (enoughTimePassed && enoughProgress) {
            lastReportAt = now
            lastPercent = percent
            percent
        } else {
            null
        }
    }

    private companion object {
        const val PROGRESS_MIN_INTERVAL_MS = 1_000L
        const val PROGRESS_MIN_PERCENTAGE_POINTS = 1
    }
}

private const val PROGRESS_PERCENT_MAX = 100

internal fun exceedsDownloadLimit(bytes: Long): Boolean =
    bytes > Constants.SecurityLimits.MAX_DOWNLOAD_BYTES

internal fun ensureDownloadChunkWithinLimit(
    bytesCopied: Long,
    nextChunkBytes: Int,
    maxBytes: Long = Constants.SecurityLimits.MAX_DOWNLOAD_BYTES
) {
    if (nextChunkBytes < 0 || bytesCopied > maxBytes - nextChunkBytes) {
        throw DownloadSizeLimitException()
    }
}

internal class DownloadSizeLimitException : IOException("Download exceeds the maximum allowed size")

internal fun ensureAvailableStorage(
    availableBytes: Long,
    requiredBytes: Long,
    reserveBytes: Long = Constants.SecurityLimits.MIN_FREE_STORAGE_RESERVE_BYTES
) {
    if (availableBytes < reserveBytes || requiredBytes < 0L || requiredBytes > availableBytes - reserveBytes) {
        throw DownloadStorageException()
    }
}

internal class DownloadStorageException : IOException("Not enough free storage for download")

internal suspend fun <T> runWithCleanupOnFailure(
    cleanup: () -> Unit,
    block: suspend () -> T
): T = try {
    block()
} catch (error: Throwable) {
    runCatching(cleanup)
    throw error
}

internal class DownloadHttpException(
    val statusCode: Int
) : IOException("Server responded with error: $statusCode")

internal fun shouldRetryDownloadFailure(
    error: Throwable,
    runAttemptCount: Int,
    maxRetryAttempts: Int
): Boolean {
    if (runAttemptCount >= maxRetryAttempts) return false
    return when (error) {
        is DownloadSizeLimitException,
        is DownloadStorageException,
        is DownloadProtocolException,
        is StaleDownloadWorkerException -> false
        is DownloadHttpException ->
            error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
        is IOException -> true
        else -> false
    }
}
