package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.guava.await
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class DownloadWorkRequest(
    val uniqueWorkName: String,
    val existingWorkPolicy: ExistingWorkPolicy,
    val request: OneTimeWorkRequest
)

internal object DownloadWorkRequestFactory {
    private const val BACKOFF_SECONDS = 30L

    fun create(episodeId: Long): DownloadWorkRequest {
        val workName = downloadWorkName(episodeId)
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(Constants.DOWNLOAD_WORKER_EPISODE_ID to episodeId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(Constants.DOWNLOAD_WORKER_TAG)
                .addTag(workName)
                .build()

        return DownloadWorkRequest(
            uniqueWorkName = workName,
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = request
        )
    }
}

internal suspend fun WorkManager.enqueueDownloadWork(episodeId: Long): UUID {
    val workRequest = DownloadWorkRequestFactory.create(episodeId)
    enqueueUniqueWork(
        workRequest.uniqueWorkName,
        workRequest.existingWorkPolicy,
        workRequest.request
    ).result.await()
    return workRequest.request.id
}
