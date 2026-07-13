package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class BackgroundSyncSchedule(
    val intervalHours: Long,
    val requiredNetworkType: NetworkType = NetworkType.CONNECTED,
    val requiresBatteryNotLow: Boolean = true,
    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL,
    val backoffDelaySeconds: Long = 30L
)

internal fun backgroundSyncSchedule(intervalHours: Int): BackgroundSyncSchedule =
    BackgroundSyncSchedule(
        intervalHours = intervalHours.coerceIn(
            Constants.SettingsDefaults.MIN_BACKGROUND_CHECK_INTERVAL_HOURS.toInt(),
            Constants.SettingsDefaults.MAX_BACKGROUND_CHECK_INTERVAL_HOURS.toInt()
        ).toLong()
    )

@Singleton
class BackgroundSyncScheduler
@Inject
constructor(
    private val workManager: WorkManager
) {
    suspend fun applySettings(
        enabled: Boolean,
        intervalHours: Int
    ) {
        if (!enabled) {
            workManager.cancelUniqueWork(Constants.FEED_UPDATE_WORK_NAME).result.await()
            return
        }

        val schedule = backgroundSyncSchedule(intervalHours)
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(schedule.requiredNetworkType)
                .setRequiresBatteryNotLow(schedule.requiresBatteryNotLow)
                .build()
        val request =
            PeriodicWorkRequestBuilder<FeedUpdateWorker>(schedule.intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    schedule.backoffPolicy,
                    schedule.backoffDelaySeconds,
                    TimeUnit.SECONDS
                )
                .addTag(Constants.FEED_UPDATE_WORK_TAG)
                .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.FEED_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        ).result.await()
    }
}
