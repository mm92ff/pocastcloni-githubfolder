package com.example.pocastcloni

import androidx.work.WorkManager
import com.example.pocastcloni.data.cover.PodcastCoverMaintenance
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.repository.BackupImportRecovery
import com.example.pocastcloni.data.worker.AppSchedulingCoordinator
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.app.ResetAppUseCase
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.RetryingDataFlow
import com.example.pocastcloni.util.activeEpisodeIdsFromDownloadWork
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates application startup in a child supervisor of the application scope.
 *
 * [initialize] claims startup atomically and is at-most-once for this singleton instance. A
 * single coordinator repairs interrupted backup imports, resumes a pending reset, and reconciles
 * episode storage, and persistent cover storage in that order. An ordinary phase failure is logged
 * without skipping later cleanup phases. Core recovery phases must succeed before long-lived
 * observers are started; cover repair remains best-effort and never blocks feed scheduling.
 *
 * The owned scope follows application-scope cancellation. Its [SupervisorJob] keeps observer
 * failures independent, while [CancellationException] is always propagated and prevents any
 * remaining startup phases or observers from starting. Ordinary observer exceptions are logged
 * at the child boundary and do not cancel sibling observers.
 */
@Singleton
class AppInitializer
@Inject
@Suppress("LongParameterList")
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val maintenance: LibraryMaintenancePort,
    private val podcastDao: PodcastDao,
    @ApplicationScope private val scope: CoroutineScope,
    private val workManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
    private val backupImportRecovery: BackupImportRecovery,
    private val resetAppUseCase: ResetAppUseCase,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry,
    private val schedulingCoordinator: AppSchedulingCoordinator,
    private val podcastCoverMaintenance: PodcastCoverMaintenance? = null
) {
    private val initialized = AtomicBoolean(false)
    private val startupCompletion = CompletableDeferred<Unit>()
    private val startupScope =
        CoroutineScope(
            scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
        )

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return

        startupScope
            .launch(dispatcherProvider.io) {
                val recoverySucceeded =
                    runStartupOperation("recover interrupted backup import") {
                        backupImportRecovery.recoverInterruptedImport()
                    }
                val resetSucceeded =
                    runStartupOperation("resume pending app reset") {
                        resetAppUseCase.resumeIfPending()
                    }
                val reconciliationSucceeded =
                    runStartupOperation("reconcile local episode storage state") {
                        reconcileEpisodeStorage()
                    }
                runStartupOperation("reconcile persistent podcast covers") {
                    podcastCoverMaintenance?.reconcileAndSchedule()
                }
                if (recoverySucceeded && resetSucceeded && reconciliationSucceeded) {
                    startLongLivedObservers()
                }
            }
            .invokeOnCompletion {
                startupCompletion.complete(Unit)
            }
    }

    /** Waits until all one-shot startup phases have either completed or been cancelled. */
    internal suspend fun awaitStartupCompletion() {
        startupCompletion.await()
    }

    private fun startLongLivedObservers() {
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
            runStartupOperation(description, block)
        }
    }

    private suspend fun runStartupOperation(
        description: String,
        block: suspend () -> Unit
    ): Boolean =
        try {
            block()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Failed to %s.", description)
            false
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
            maintenance.reconcileEpisodeStorage(activeEpisodeIds) { episodeId ->
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
