package com.example.pocastcloni.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat // Critical Import for setOngoing
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.FeedFailureKind
import com.example.pocastcloni.domain.model.classifyFeedFailure
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@HiltWorker
class FeedUpdateWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val feedUpdateRunner: FeedUpdateRunner,
    private val feedRetryScheduler: FeedRetryScheduler
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val source =
                inputData.getString(KEY_REFRESH_SOURCE)
                    ?.let { storedSource ->
                        runCatching { FeedRefreshSource.valueOf(storedSource) }.getOrNull()
                    }
                    ?: FeedRefreshSource.BACKGROUND
            val targetFeedUrl = inputData.getString(KEY_FEED_URL)?.trim()?.takeIf(String::isNotEmpty)
            if (feedUpdateRequiresForeground(targetFeedUrl)) {
                setForeground(createForegroundInfo())
            }
            Timber.d("Starting feed update...")
            val summary = feedUpdateRunner(source, targetFeedUrl?.let(::setOf))

            if (summary.hasFailures) {
                Timber.w(
                    "Feed update completed with %d successes and %d failures.",
                    summary.successfulCount,
                    summary.failureCount
                )
            }

            if (targetFeedUrl == null) {
                feedRetryScheduler.schedule(summary.retryableFailedUrls)
                return Result.success()
            }

            when {
                !summary.hasFailures || summary.isEmpty -> Result.success()
                summary.retryableFailedUrls.contains(targetFeedUrl) || summary.failures.isEmpty() ->
                    retryOrFail()
                else -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in FeedUpdateWorker")
            if (classifyFeedFailure(e) == FeedFailureKind.RETRYABLE) retryOrFail() else Result.failure()
        }
    }

    private fun retryOrFail(): Result =
        if (WorkerRetryPolicy.canRetry(runAttemptCount)) Result.retry() else Result.failure()

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "sync_channel"
        // Use fallbacks if strings are missing to prevent compile errors,
        // but YOU SHOULD add these to strings.xml.
        val title = context.getString(R.string.sync_notification_title)
        val content = context.getString(R.string.sync_notification_content)
        val channelName = context.getString(R.string.sync_notification_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(content)
                // Use a standard Android icon to fix the "Unresolved Reference" immediately
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_REFRESH_SOURCE = "refresh_source"
        const val KEY_FEED_URL = "feed_url"
        private const val NOTIFICATION_ID = 1001
    }
}

/** Single-feed retries stay within regular WorkManager execution and preserve the shared FGS budget. */
internal fun feedUpdateRequiresForeground(targetFeedUrl: String?): Boolean = targetFeedUrl == null
