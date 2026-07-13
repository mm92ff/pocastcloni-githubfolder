package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class FeedRetrySchedule(
    val requiredNetworkType: NetworkType = NetworkType.CONNECTED,
    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL,
    val backoffDelaySeconds: Long = 30L
)

@Singleton
class FeedRetryScheduler
@Inject
constructor(
    private val workManager: WorkManager
) {
    fun schedule(feedUrls: Collection<String>) {
        feedUrls.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach(::scheduleFeed)
    }

    private fun scheduleFeed(feedUrl: String) {
        val schedule = FeedRetrySchedule()
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(schedule.requiredNetworkType)
                .build()
        val input =
            Data.Builder()
                .putString(FeedUpdateWorker.KEY_REFRESH_SOURCE, FeedRefreshSource.BACKGROUND.name)
                .putString(FeedUpdateWorker.KEY_FEED_URL, feedUrl)
                .build()
        val request =
            OneTimeWorkRequestBuilder<FeedUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    schedule.backoffPolicy,
                    schedule.backoffDelaySeconds,
                    TimeUnit.SECONDS
                )
                .setInputData(input)
                .addTag(WORK_TAG)
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(feedUrl),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_TAG = "FeedRetryWorkTag"
        private const val WORK_NAME_PREFIX = "FeedRetry_"

        internal fun uniqueWorkName(feedUrl: String): String {
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(feedUrl.toByteArray(StandardCharsets.UTF_8))
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }
            return WORK_NAME_PREFIX + digest
        }
    }
}
