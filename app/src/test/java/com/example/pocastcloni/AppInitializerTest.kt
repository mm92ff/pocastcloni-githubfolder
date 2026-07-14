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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `one-shot phases finish in order before observers start`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(emptyList())
        every { workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG) } returns
            Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery { recovery.recoverInterruptedImport() } coAnswers {
            events += "recovery started"
            delay(10)
            events += "recovery finished"
        }
        coEvery { resetApp.resumeIfPending() } coAnswers {
            events += "reset started"
            delay(10)
            events += "reset finished"
            false
        }
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } coAnswers {
            events += "reconciliation started"
            delay(10)
            events += "reconciliation finished"
            0
        }
        every { localRegistry.replaceApprovedFeeds(any()) } answers {
            events += "local feed observer"
        }
        coEvery {
            schedulingCoordinator.applyObservedCleanupSettings(any(), any())
        } coAnswers {
            events += "cleanup observer"
        }
        coEvery {
            schedulingCoordinator.applyObservedBackgroundSettings(any(), any())
        } coAnswers {
            events += "background observer"
        }

        initializer(backgroundScope).initialize()
        runCurrent()
        assertEquals(listOf("recovery started"), events)

        advanceTimeBy(10)
        runCurrent()
        assertEquals(
            listOf("recovery started", "recovery finished", "reset started"),
            events
        )

        advanceTimeBy(10)
        runCurrent()
        assertEquals(
            listOf(
                "recovery started",
                "recovery finished",
                "reset started",
                "reset finished",
                "reconciliation started"
            ),
            events
        )

        advanceTimeBy(10)
        runCurrent()

        assertEquals(
            listOf(
                "recovery started",
                "recovery finished",
                "reset started",
                "reset finished",
                "reconciliation started",
                "reconciliation finished"
            ),
            events.take(6)
        )
        assertEquals(
            setOf("local feed observer", "cleanup observer", "background observer"),
            events.drop(6).toSet()
        )
    }

    @Test
    fun `duplicate initialize calls start work only once`() = runTest(dispatcher) {
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(emptyList())
        every { workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG) } returns
            Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery { recovery.recoverInterruptedImport() } returns Unit
        coEvery { resetApp.resumeIfPending() } returns false
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } returns 0
        val initializer = initializer(backgroundScope)

        backgroundScope.launch { initializer.initialize() }
        backgroundScope.launch { initializer.initialize() }
        runCurrent()

        initializer.initialize()
        runCurrent()

        coVerify(exactly = 1) { recovery.recoverInterruptedImport() }
        coVerify(exactly = 1) { resetApp.resumeIfPending() }
        coVerify(exactly = 1) { repository.reconcileEpisodeStorage(any(), any()) }
        verify(exactly = 1) { localRegistry.replaceApprovedFeeds(emptyList()) }
        coVerify(exactly = 1) {
            schedulingCoordinator.applyObservedCleanupSettings(any(), any())
        }
        coVerify(exactly = 1) {
            schedulingCoordinator.applyObservedBackgroundSettings(any(), any())
        }
    }

    @Test
    fun `failed import recovery allows later cleanup but prevents observers`() = runTest(dispatcher) {
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
        coVerify(exactly = 0) { schedulingCoordinator.applyObservedBackgroundSettings(any(), any()) }
        coVerify(exactly = 0) { schedulingCoordinator.applyObservedCleanupSettings(any(), any()) }
        verify(exactly = 0) { localRegistry.replaceApprovedFeeds(any()) }
    }

    @Test
    fun `parent cancellation during one-shot work prevents later startup`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val applicationJob = Job(backgroundScope.coroutineContext[Job])
        val applicationScope = CoroutineScope(backgroundScope.coroutineContext + applicationJob)
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(emptyList())
        coEvery { recovery.recoverInterruptedImport() } coAnswers {
            events += "recovery started"
            delay(100)
            events += "recovery finished"
        }
        coEvery { resetApp.resumeIfPending() } returns false
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } returns 0

        initializer(applicationScope).initialize()
        runCurrent()
        assertEquals(listOf("recovery started"), events)

        applicationJob.cancel()
        runCurrent()

        assertEquals(listOf("recovery started"), events)
        coVerify(exactly = 0) { resetApp.resumeIfPending() }
        coVerify(exactly = 0) { repository.reconcileEpisodeStorage(any(), any()) }
        verify(exactly = 0) { localRegistry.replaceApprovedFeeds(any()) }
        coVerify(exactly = 0) {
            schedulingCoordinator.applyObservedCleanupSettings(any(), any())
        }
        coVerify(exactly = 0) {
            schedulingCoordinator.applyObservedBackgroundSettings(any(), any())
        }
    }

    @Test
    fun `failing observer child does not cancel sibling observers`() = runTest(dispatcher) {
        every { dispatcherProvider.io } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        every { podcastDao.getApprovedLocalFeedUrlsFlow() } returns flowOf(listOf("http://lan/feed"))
        every { workManager.getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG) } returns
            Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery { recovery.recoverInterruptedImport() } returns Unit
        coEvery { resetApp.resumeIfPending() } returns false
        coEvery { repository.reconcileEpisodeStorage(any(), any()) } returns 0
        coEvery {
            schedulingCoordinator.applyObservedCleanupSettings(any(), any())
        } throws IOException("cleanup scheduling failed")

        initializer(backgroundScope).initialize()
        runCurrent()

        verify(exactly = 1) { localRegistry.replaceApprovedFeeds(listOf("http://lan/feed")) }
        coVerify(exactly = 1) {
            schedulingCoordinator.applyObservedCleanupSettings(any(), any())
        }
        coVerify(exactly = 1) {
            schedulingCoordinator.applyObservedBackgroundSettings(any(), any())
        }
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
