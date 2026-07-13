package com.example.pocastcloni.data.worker

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import kotlinx.coroutines.guava.await
import javax.inject.Inject
import javax.inject.Singleton

internal data class BackupPostImportSyncSchedule(
    val requiredNetworkType: NetworkType = NetworkType.CONNECTED,
    val requiresBatteryNotLow: Boolean = true,
    val refreshSource: FeedRefreshSource = FeedRefreshSource.BACKUP_RESTORE
)

@Singleton
class BackupPostImportSyncScheduler
@Inject
constructor(
    private val workManager: WorkManager
) {
    suspend fun schedule() {
        val schedule = BackupPostImportSyncSchedule()
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(schedule.requiredNetworkType)
                .setRequiresBatteryNotLow(schedule.requiresBatteryNotLow)
                .build()
        val input =
            Data.Builder()
                .putString(FeedUpdateWorker.KEY_REFRESH_SOURCE, schedule.refreshSource.name)
                .build()
        val request =
            OneTimeWorkRequestBuilder<FeedUpdateWorker>()
                .setConstraints(constraints)
                .setInputData(input)
                .addTag(POST_IMPORT_WORK_TAG)
                .build()

        workManager.enqueueUniqueWork(
            POST_IMPORT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        ).result.await()
    }

    companion object {
        const val POST_IMPORT_WORK_NAME = "BackupPostImportFeedUpdate"
        const val POST_IMPORT_WORK_TAG = "BackupPostImportFeedUpdateTag"
    }
}
