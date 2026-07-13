package com.example.pocastcloni

import androidx.work.WorkManager
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.repository.BackupImportRecovery
import com.example.pocastcloni.data.worker.AppSchedulingCoordinator
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.app.ResetAppUseCase
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.RetryingDataFlow
import com.example.pocastcloni.util.activeEpisodeIdsFromDownloadWork
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastRepository: PodcastRepository,
    private val podcastDao: PodcastDao,
    @ApplicationScope private val scope: CoroutineScope,
    private val workManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
    private val backupImportRecovery: BackupImportRecovery,
    private val resetAppUseCase: ResetAppUseCase,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry,
    private val schedulingCoordinator: AppSchedulingCoordinator
) {
    private val startupScope =
        CoroutineScope(
            scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
        )

    fun initialize() {
        launchStartupChild("recover interrupted backup import") {
            backupImportRecovery.recoverInterruptedImport()
        }
        launchStartupChild("resume pending app reset") {
            resetAppUseCase.resumeIfPending()
        }
        launchStartupChild("reconcile local episode storage state") {
            reconcileEpisodeStorage()
        }
        launchStartupChild("observe approved local feeds") {
            observeApprovedLocalFeeds()
        }
        launchStartupChild("observe cleanup settings") {
            observeCleanupSettings()
        }
        launchStartupChild("observe background sync settings") {
            observeBackgroundSyncSettings()
        }
    }

    private fun launchStartupChild(
        description: String,
        block: suspend () -> Unit
    ) {
        startupScope.launch(dispatcherProvider.io) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Failed to %s.", description)
            }
        }
    }

    private suspend fun observeApprovedLocalFeeds() {
        RetryingDataFlow.bounded(podcastDao.getApprovedLocalFeedUrlsFlow())
            .distinctUntilChanged()
            .collect(localNetworkAccessRegistry::replaceApprovedFeeds)
    }

    private suspend fun observeBackgroundSyncSettings() {
        userPreferencesRepository.userSettingsFlow
            .map { it.backgroundCheckEnabled to it.backgroundCheckInterval }
            .distinctUntilChanged()
            .collect { (isEnabled, hours) ->
                schedulingCoordinator.applyObservedBackgroundSettings(isEnabled, hours)
            }
    }

    private suspend fun reconcileEpisodeStorage() {
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
    }

    private suspend fun observeCleanupSettings() {
        userPreferencesRepository.userSettingsFlow
            .map { it.autoCleanupEnabled to it.cleanupIntervalHours }
            .distinctUntilChanged()
            .collect { (isEnabled, hours) ->
                schedulingCoordinator.applyObservedCleanupSettings(isEnabled, hours)
            }
    }
}
