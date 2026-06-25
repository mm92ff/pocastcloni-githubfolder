package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class LibraryCleanupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val WORK_NAME = "LibraryCleanupWork"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting library cleanup job...")

            val settings = userPreferencesRepository.userSettingsFlow.first()
            val limit = settings.cleanupKeepLimit

            podcastRepository.pruneLibrary(limit)

            Timber.d("Library cleanup finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Library cleanup failed")
            Result.retry()
        }
    }
}
