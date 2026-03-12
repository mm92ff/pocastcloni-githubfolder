package com.example.pocastcloni

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.data.worker.FeedUpdateWorker
import com.example.pocastcloni.data.worker.LibraryCleanupWorker // Import hinzufügen
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastRepository: PodcastRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val workManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider
) {

    fun initialize() {
        reconcileEpisodeStorage()
        // Bereinigungs-Job einplanen (Täglich)
        setupLibraryCleanup()

        scope.launch(dispatcherProvider.io) {
            try {
                userPreferencesRepository.userSettingsFlow
                    .map { it.backgroundCheckEnabled to it.backgroundCheckInterval }
                    .distinctUntilChanged()
                    .catch { e ->
                        Timber.e(e, "Error collecting user settings for background sync.")
                    }
                    .collect { (isEnabled, hours) ->
                        try {
                            if (isEnabled) {
                                setupBackgroundSync(hours)
                            } else {
                                Timber.d("Background sync disabled by user. Cancelling work.")
                                workManager.cancelUniqueWork(Constants.FEED_UPDATE_WORK_NAME)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to apply background sync settings change.")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Fatal error during AppInitializer execution. Background sync might not be configured.")
            }
        }
    }

    private fun reconcileEpisodeStorage() {
        scope.launch(dispatcherProvider.io) {
            runCatching {
                val correctedEntries = podcastRepository.reconcileEpisodeStorage()
                if (correctedEntries > 0) {
                    Timber.i("Reconciled %d stale episode storage states on startup.", correctedEntries)
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to reconcile local episode storage state.")
            }
        }
    }

    private fun setupLibraryCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true) // Läuft nur, wenn Handy nicht genutzt wird
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<LibraryCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            LibraryCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
        Timber.i("Library cleanup scheduled (Daily, Idle, Battery OK).")
    }

    private fun setupBackgroundSync(intervalHours: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val safeHours = intervalHours
            .coerceAtLeast(Constants.MIN_BACKGROUND_SYNC_INTERVAL_HOURS)
            .toLong()

        val updateRequest = PeriodicWorkRequestBuilder<FeedUpdateWorker>(safeHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(Constants.FEED_UPDATE_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.FEED_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )

        Timber.i("Background sync scheduled every $safeHours hours with network constraints.")
    }
}
