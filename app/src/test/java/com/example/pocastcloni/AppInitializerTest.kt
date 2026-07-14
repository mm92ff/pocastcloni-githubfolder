package com.example.pocastcloni

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.repository.BackupImportRecovery
import com.example.pocastcloni.data.worker.AppSchedulingCoordinator
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.ResetAppUseCase
import com.example.pocastcloni.util.Constants
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppInitializerTest {
    private val dispatcher = StandardTestDispatcher()
    private val preferences = mockk<UserPreferencesRepository>()
    private val repository = mockk<LibraryMaintenancePort>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>()
    private val workManager = mockk<WorkManager>()
    private val dispatcherProvider = mockk<DispatcherProvider>()
    private val recovery = mockk<BackupImportRecovery>()
    private val resetApp = mockk<ResetAppUseCase>()
    private val localRegistry = mockk<LocalNetworkAccessRegistry>(relaxed = true)
    private val schedulingCoordinator = mockk<AppSchedulingCoordinator>(relaxed = true)

    @Test
    fun `failed import recovery does not prevent reset reconciliation or scheduling`() = runTest(dispatcher) {
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(listOf("http://lan/feed"))
        every { workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG) } returns
            Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery { recovery.recoverInterruptedImport() } throws IOException("broken journal")
        coEvery { resetApp.resumeIfPending() } returns false
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } returns 0

        initializer(backgroundScope).initialize()
        runCurrent()

        coVerify(exactly = 1) { recovery.recoverInterruptedImport() }
        coVerify(exactly = 1) { resetApp.resumeIfPending() }
        coVerify(exactly = 1) { repository.reconcileEpisodeStorage(emptySet(), any()) }
        coVerify(exactly = 1) { schedulingCoordinator.applyObservedBackgroundSettings(any(), any()) }
        coVerify(exactly = 1) { schedulingCoordinator.applyObservedCleanupSettings(any(), any()) }
        verify(exactly = 1) { localRegistry.replaceApprovedFeeds(listOf("http://lan/feed")) }
    }

    @Test
    fun `one settings change produces one background scheduler update`() = runTest(dispatcher) {
        val settings = MutableStateFlow(UserSettings(backgroundCheckEnabled = true, backgroundCheckInterval = 1))
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns settings
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(emptyList())
        every { workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG) } returns
            Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery { recovery.recoverInterruptedImport() } returns Unit
        coEvery { resetApp.resumeIfPending() } returns false
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } returns 0
        initializer(backgroundScope).initialize()
        runCurrent()
        coVerify(exactly = 1) { schedulingCoordinator.applyObservedBackgroundSettings(true, 1) }

        settings.value = settings.value.copy(backgroundCheckInterval = 6)
        runCurrent()

        coVerify(exactly = 1) { schedulingCoordinator.applyObservedBackgroundSettings(true, 6) }
        coVerify(exactly = 2) { schedulingCoordinator.applyObservedBackgroundSettings(any(), any()) }
    }

    private fun initializer(scope: kotlinx.coroutines.CoroutineScope) =
        AppInitializer(
            userPreferencesRepository = preferences,
            maintenance = repository,
            podcastDao = podcastDao,
            scope = scope,
            workManager = workManager,
            dispatcherProvider = dispatcherProvider,
            backupImportRecovery = recovery,
            resetAppUseCase = resetApp,
            localNetworkAccessRegistry = localRegistry,
            schedulingCoordinator = schedulingCoordinator
        )
}
