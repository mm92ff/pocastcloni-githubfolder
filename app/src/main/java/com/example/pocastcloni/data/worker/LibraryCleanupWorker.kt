package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@HiltWorker
class LibraryCleanupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cleanupRunner: LibraryCleanupRunner
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val WORK_NAME = "LibraryCleanupWork"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting library cleanup job...")

            if (cleanupRunner()) {
                Timber.d("Library cleanup finished successfully.")
            } else {
                Timber.d("Library cleanup skipped because auto cleanup is disabled.")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Library cleanup failed")
            Result.retry()
        }
    }
}
