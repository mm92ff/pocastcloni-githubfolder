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
    private val feedUpdateRunner: FeedUpdateRunner
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            // 1. Promote to Foreground Service
            // This is required for operations that might take > 10 minutes or use network heavily.
            setForeground(createForegroundInfo())

            Timber.d("Starting feed update...")

            // 2. Perform Update
            try {
                val source = inputData.getString(KEY_REFRESH_SOURCE)
                    ?.let { storedSource ->
                        runCatching { FeedRefreshSource.valueOf(storedSource) }.getOrNull()
                    }
                    ?: FeedRefreshSource.BACKGROUND
                val summary = feedUpdateRunner(source)
                if (summary.allFailed) {
                    Timber.w("Feed update failed for all %d podcasts.", summary.totalCount)
                } else if (summary.hasFailures) {
                    Timber.w(
                        "Feed update partially failed: %d succeeded, %d failed.",
                        summary.successfulCount,
                        summary.failureCount
                    )
                } else {
                    Timber.d("Update finished: %d podcasts refreshed.", summary.successfulCount)
                }

                if (summary.allFailed) {
                    return if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error updating podcasts.")
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in FeedUpdateWorker")
            Result.failure()
        }
    }

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
        private const val NOTIFICATION_ID = 1001
    }
}
