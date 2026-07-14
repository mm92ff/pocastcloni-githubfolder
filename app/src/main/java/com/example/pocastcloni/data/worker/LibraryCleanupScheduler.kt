package com.example.pocastcloni.data.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class LibraryCleanupSchedule(
    val intervalHours: Long,
    val requiresDeviceIdle: Boolean = true,
    val requiresBatteryNotLow: Boolean = true
)

internal fun libraryCleanupSchedule(intervalHours: Int): LibraryCleanupSchedule =
    LibraryCleanupSchedule(
        intervalHours = intervalHours.coerceIn(
            Constants.SettingsDefaults.MIN_CLEANUP_INTERVAL_HOURS.toInt(),
            Constants.SettingsDefaults.MAX_CLEANUP_INTERVAL_HOURS.toInt()
        ).toLong()
    )

@Singleton
class LibraryCleanupScheduler
@Inject
constructor(
    private val workManager: WorkManager
) {
    suspend fun applySettings(
        enabled: Boolean,
        intervalHours: Int
    ) {
        if (!enabled) {
            workManager.cancelUniqueWork(LibraryCleanupWorker.WORK_NAME).result.await()
            return
        }

        val schedule = libraryCleanupSchedule(intervalHours)
        val constraints =
            Constraints.Builder()
                .setRequiresDeviceIdle(schedule.requiresDeviceIdle)
                .setRequiresBatteryNotLow(schedule.requiresBatteryNotLow)
                .build()
        val request =
            PeriodicWorkRequestBuilder<LibraryCleanupWorker>(schedule.intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            LibraryCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        ).result.await()
    }
}
