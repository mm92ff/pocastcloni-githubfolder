package com.example.pocastcloni

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.data.worker.FeedUpdateWorker
import com.example.pocastcloni.data.repository.BackupImportRecovery
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.worker.LibraryCleanupScheduler
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.activeEpisodeIdsFromDownloadWork
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastRepository: PodcastRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val workManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
    private val backupImportRecovery: BackupImportRecovery,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry,
    private val libraryCleanupScheduler: LibraryCleanupScheduler
) {
    fun initialize() {
        observeApprovedLocalFeeds()
        scope.launch(dispatcherProvider.io) {
            try {
                backupImportRecovery.recoverInterruptedImport()
            } catch (error: Exception) {
                Timber.e(error, "Failed to recover interrupted backup import")
                return@launch
            }
            reconcileEpisodeStorage()
            observeCleanupSettings()
            observeBackgroundSyncSettings()
        }
    }

    private fun observeApprovedLocalFeeds() {
        scope.launch(dispatcherProvider.io) {
            podcastRepository.getAllPodcastsFlow()
                .catch { error -> Timber.e(error, "Failed to load approved local feeds") }
                .collect { podcasts ->
                    localNetworkAccessRegistry.replaceApprovedFeeds(
                        podcasts.filter { it.allowLocalNetwork }.map { it.rssUrl }
                    )
                }
        }
    }

    private fun observeBackgroundSyncSettings() {
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

    private suspend fun reconcileEpisodeStorage() {
        try {
            val workInfos = workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG).await()
            val activeEpisodeIds = activeEpisodeIdsFromDownloadWork(workInfos)
            val correctedEntries =
                podcastRepository.reconcileEpisodeStorage(activeEpisodeIds) { episodeId ->
                    val currentWork = workManager.getWorkInfosByTag(downloadWorkName(episodeId)).await()
                    episodeId in activeEpisodeIdsFromDownloadWork(currentWork)
                }
            if (correctedEntries > 0) {
                Timber.i("Reconciled %d stale episode storage states on startup.", correctedEntries)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Failed to reconcile local episode storage state.")
        }
    }

    private fun observeCleanupSettings() {
        scope.launch(dispatcherProvider.io) {
            try {
                userPreferencesRepository.userSettingsFlow
                    .map { it.autoCleanupEnabled to it.cleanupIntervalHours }
                    .distinctUntilChanged()
                    .catch { e ->
                        Timber.e(e, "Error collecting cleanup settings.")
                    }
                    .collect { (isEnabled, hours) ->
                        try {
                            libraryCleanupScheduler.applySettings(isEnabled, hours)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to apply cleanup settings change.")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Fatal error in cleanup settings observer.")
            }
        }
    }

    private fun setupBackgroundSync(intervalHours: Int) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val safeHours =
            intervalHours
                .coerceAtLeast(Constants.MIN_BACKGROUND_SYNC_INTERVAL_HOURS)
                .toLong()

        val updateRequest =
            PeriodicWorkRequestBuilder<FeedUpdateWorker>(safeHours, TimeUnit.HOURS)
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
