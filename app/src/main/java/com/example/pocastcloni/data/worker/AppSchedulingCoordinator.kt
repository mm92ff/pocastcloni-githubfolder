package com.example.pocastcloni.data.worker

import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.domain.repository.UserSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSchedulingCoordinator
@Inject
constructor(
    private val markerStore: AppResetMarkerStore,
    private val backgroundSyncScheduler: BackgroundSyncScheduler,
    private val libraryCleanupScheduler: LibraryCleanupScheduler
) {
    private val schedulingMutex = Mutex()
    private val resetEpoch = AtomicLong()

    suspend fun applyObservedBackgroundSettings(
        enabled: Boolean,
        intervalHours: Int
    ) {
        val observedEpoch = resetEpoch.get()
        schedulingMutex.withLock {
            if (observedEpoch != resetEpoch.get() || markerStore.isPending()) return@withLock
            backgroundSyncScheduler.applySettings(enabled, intervalHours)
        }
    }

    suspend fun applyObservedCleanupSettings(
        enabled: Boolean,
        intervalHours: Int
    ) {
        val observedEpoch = resetEpoch.get()
        schedulingMutex.withLock {
            if (observedEpoch != resetEpoch.get() || markerStore.isPending()) return@withLock
            libraryCleanupScheduler.applySettings(enabled, intervalHours)
        }
    }

    suspend fun runResetAndReconcile(
        reset: suspend () -> Unit,
        loadFinalSettings: suspend () -> UserSettings,
        completeReset: () -> Unit
    ) {
        schedulingMutex.withLock {
            resetEpoch.incrementAndGet()
            try {
                reset()
                reconcile(loadFinalSettings())
                completeReset()
            } finally {
                resetEpoch.incrementAndGet()
            }
        }
    }

    private suspend fun reconcile(settings: UserSettings) {
        backgroundSyncScheduler.applySettings(
            settings.backgroundCheckEnabled,
            settings.backgroundCheckInterval
        )
        libraryCleanupScheduler.applySettings(
            settings.autoCleanupEnabled,
            settings.cleanupIntervalHours
        )
    }
}
