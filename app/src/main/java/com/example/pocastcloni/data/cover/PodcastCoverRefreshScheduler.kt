package com.example.pocastcloni.data.cover

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.PodcastCoverStateDao
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.worker.PodcastCoverRefreshWorker
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastCoverRefreshScheduler
@Inject
constructor(
    private val workManager: WorkManager,
    private val podcastDao: PodcastDao,
    private val coverStateDao: PodcastCoverStateDao,
    private val clock: SystemPodcastCoverClock
) {
    suspend fun enqueue(
        podcastRssUrl: String,
        force: Boolean = false,
        replaceExisting: Boolean = false
    ) {
        val initialDelayMs = if (force) 0L else calculateInitialDelay(podcastRssUrl)
        val request =
            OneTimeWorkRequestBuilder<PodcastCoverRefreshWorker>()
                .setConstraints(COVER_WORK_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(PodcastCoverRefreshWorker.KEY_PODCAST_RSS_URL, podcastRssUrl)
                        .putBoolean(PodcastCoverRefreshWorker.KEY_FORCE, force)
                        .build()
                )
                .addTag(WORK_TAG)
                .build()
        workManager.enqueueUniqueWork(
            workName(podcastRssUrl),
            if (force || replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun calculateInitialDelay(podcastRssUrl: String): Long {
        val state = coverStateDao.getState(podcastRssUrl) ?: return 0L
        val now = clock.now()
        val retryDelay =
            state.nextRetryAt
                ?.let { (it - now).coerceIn(0L, MAX_RETRY_DELAY_MS) }
                ?: 0L
        val cooldownDelay =
            if (state.thumbnailFileName != null && state.lastSuccessfulCheckAt != null) {
                val elapsed = (now - state.lastSuccessfulCheckAt).coerceAtLeast(0L)
                (PodcastCoverRefreshPolicy.REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(0L)
            } else {
                0L
            }
        return maxOf(retryDelay, cooldownDelay)
    }

    suspend fun enqueueAll(force: Boolean = false) {
        coverStateDao.seedMissingPodcastStates(clock.now())
        podcastDao.getAllPodcastUrls().forEach { rssUrl -> enqueue(rssUrl, force) }
    }

    fun cancel(podcastRssUrl: String) {
        workManager.cancelUniqueWork(workName(podcastRssUrl))
    }

    private fun workName(podcastRssUrl: String): String = "$WORK_NAME_PREFIX${podcastRssUrl.sha256Prefix()}"

    private fun String.sha256Prefix(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(HASH_BYTES)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    companion object {
        const val WORK_TAG = "podcast_cover_refresh"
        private const val RETRY_DELAY_MINUTES = 15L
        private const val MAX_RETRY_DELAY_MS = 24L * 60L * 60L * 1000L
        private const val WORK_NAME_PREFIX = "podcast_cover_"
        private const val HASH_BYTES = 16
        private const val BYTE_MASK = 0xff
        private val COVER_WORK_CONSTRAINTS =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
    }
}
