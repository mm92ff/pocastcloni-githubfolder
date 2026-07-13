package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache as MediaCache
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.data.worker.AppSchedulingCoordinator
import com.example.pocastcloni.data.worker.BackgroundSyncScheduler
import com.example.pocastcloni.data.worker.LibraryCleanupScheduler
import com.example.pocastcloni.data.worker.LibraryCleanupWorker
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@AndroidxOptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalCoilApi::class)
class ResetAppUseCaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val podcastRepository = mockk<PodcastRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val mediaCache = mockk<MediaCache>(relaxed = true)
    private val mediaCacheProvider = MediaCacheProvider { mediaCache }
    private val imageLoader = mockk<ImageLoader>()
    private val memoryCache = mockk<MemoryCache>(relaxed = true)
    private val diskCache = mockk<DiskCache>(relaxed = true)
    private val defaultHttpCache = mockk<Cache>(relaxed = true)
    private val localHttpCache = mockk<Cache>(relaxed = true)
    private val approvedMediaHttpCache = mockk<Cache>(relaxed = true)
    private val okHttpClient = clientWith(defaultHttpCache)
    private val localNetworkClient = clientWith(localHttpCache)
    private val approvedMediaClient = clientWith(approvedMediaHttpCache)
    private val dispatcherProvider = mockk<DispatcherProvider>().also {
        every { it.io } returns dispatcher
    }
    private val context = mockk<Context>()
    private val workManager = mockk<WorkManager>()
    private val cancelOperation = mockk<Operation>()
    private val scheduleOperation = mockk<Operation>(relaxed = true)

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var externalDownloadsDir: File
    private lateinit var noBackupFilesDir: File
    private lateinit var markerStore: AppResetMarkerStore
    private lateinit var resetApp: ResetAppUseCase
    private val scheduledWorkNames = mutableListOf<String>()

    @Before
    fun setUp() {
        cacheDir = temporaryFolder.newFolder("cache")
        filesDir = temporaryFolder.newFolder("files")
        externalDownloadsDir = temporaryFolder.newFolder("external", "MediaStore", "Downloads")
        noBackupFilesDir = temporaryFolder.newFolder("no-backup")
        every { context.cacheDir } returns cacheDir
        every { context.noBackupFilesDir } returns noBackupFilesDir
        every { cancelOperation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { scheduleOperation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { workManager.cancelAllWork() } returns cancelOperation
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } answers {
            assertTrue(markerStore.isPending())
            scheduledWorkNames += firstArg<String>()
            scheduleOperation
        }
        every { imageLoader.memoryCache } returns memoryCache
        every { imageLoader.diskCache } returns diskCache
        every { userPreferencesRepository.userSettingsFlow } returns flowOf(UserSettings())
        mediaCacheProvider.getCacheOrNull()
        markerStore = AppResetMarkerStore(context)
        val schedulingCoordinator =
            AppSchedulingCoordinator(
                markerStore = markerStore,
                backgroundSyncScheduler = BackgroundSyncScheduler(workManager),
                libraryCleanupScheduler = LibraryCleanupScheduler(workManager)
            )
        resetApp =
            ResetAppUseCase(
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                mediaCacheProvider = mediaCacheProvider,
                imageLoader = imageLoader,
                okHttpClient = okHttpClient,
                localNetworkClient = localNetworkClient,
                approvedMediaClient = approvedMediaClient,
                workManager = workManager,
                markerStore = markerStore,
                schedulingCoordinator = schedulingCoordinator,
                dispatcherProvider = dispatcherProvider,
                context = context
            )
    }

    @Test
    fun `normal reset keeps marker until both scheduling operations complete`() = runTest(dispatcher) {
        val backgroundResult = SettableFuture.create<Operation.State.SUCCESS>()
        val cleanupResult = SettableFuture.create<Operation.State.SUCCESS>()
        stubScheduleOperation(Constants.FEED_UPDATE_WORK_NAME, backgroundResult)
        stubScheduleOperation(LibraryCleanupWorker.WORK_NAME, cleanupResult)

        val reset = backgroundScope.async { resetApp() }
        runCurrent()

        assertTrue(markerStore.isPending())
        assertFalse(reset.isCompleted)

        backgroundResult.set(Operation.SUCCESS)
        runCurrent()

        assertTrue(markerStore.isPending())
        assertFalse(reset.isCompleted)

        cleanupResult.set(Operation.SUCCESS)
        runCurrent()
        reset.await()

        assertFalse(markerStore.isPending())
    }

    @Test
    fun `normal reset retains marker when scheduling operation fails`() = runTest(dispatcher) {
        val backgroundResult = SettableFuture.create<Operation.State.SUCCESS>()
        stubScheduleOperation(Constants.FEED_UPDATE_WORK_NAME, backgroundResult)
        backgroundResult.setException(IOException("background scheduling failed"))

        try {
            resetApp()
            fail("Expected scheduling failure")
        } catch (error: IOException) {
            assertEquals("background scheduling failed", error.message)
        }

        assertTrue(markerStore.isPending())
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(
                LibraryCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        }
    }

    @Test
    fun `pending resume keeps marker until both scheduling operations complete`() = runTest(dispatcher) {
        markerStore.markPending()
        val backgroundResult = SettableFuture.create<Operation.State.SUCCESS>()
        val cleanupResult = SettableFuture.create<Operation.State.SUCCESS>()
        stubScheduleOperation(Constants.FEED_UPDATE_WORK_NAME, backgroundResult)
        stubScheduleOperation(LibraryCleanupWorker.WORK_NAME, cleanupResult)

        val resume = backgroundScope.async { resetApp.resumeIfPending() }
        runCurrent()

        assertTrue(markerStore.isPending())
        assertFalse(resume.isCompleted)

        backgroundResult.set(Operation.SUCCESS)
        runCurrent()

        assertTrue(markerStore.isPending())
        assertFalse(resume.isCompleted)

        cleanupResult.set(Operation.SUCCESS)
        runCurrent()

        assertTrue(resume.await())
        assertFalse(markerStore.isPending())
    }

    @Test
    fun `pending resume retains marker when scheduling operation fails`() = runTest(dispatcher) {
        markerStore.markPending()
        val backgroundResult = Futures.immediateFuture(Operation.SUCCESS)
        val cleanupResult = SettableFuture.create<Operation.State.SUCCESS>()
        stubScheduleOperation(Constants.FEED_UPDATE_WORK_NAME, backgroundResult)
        stubScheduleOperation(LibraryCleanupWorker.WORK_NAME, cleanupResult)
        cleanupResult.setException(IOException("cleanup scheduling failed"))

        try {
            resetApp.resumeIfPending()
            fail("Expected scheduling failure")
        } catch (error: IOException) {
            assertEquals("cleanup scheduling failed", error.message)
        }

        assertTrue(markerStore.isPending())
    }

    @Test
    fun `default settings are rescheduled after normal reset`() = runTest(dispatcher) {
        resetApp()

        assertEquals(
            listOf(Constants.FEED_UPDATE_WORK_NAME, LibraryCleanupWorker.WORK_NAME),
            scheduledWorkNames
        )
        verifyOrder {
            workManager.cancelAllWork()
            workManager.enqueueUniquePeriodicWork(
                Constants.FEED_UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
            workManager.enqueueUniquePeriodicWork(
                LibraryCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        }
        assertTrue(!markerStore.isPending())
    }

    @Test
    fun `reset removes every managed cache and retains downloads`() = runTest(dispatcher) {
        createManagedCacheMarkers()
        val internalDownload = createMarker(filesDir.resolve(Constants.DOWNLOADS_DIR))
        val externalDownload = createMarker(externalDownloadsDir)

        resetApp()

        assertTrue(Constants.Cache.MANAGED_CACHE_DIRS.all { !cacheDir.resolve(it).exists() })
        assertTrue(internalDownload.exists())
        assertTrue(externalDownload.exists())
        coVerify(exactly = 1) { userPreferencesRepository.clearSettings() }
        coVerify(exactly = 1) { podcastRepository.resetDatabase() }
        verify(exactly = 1) { workManager.cancelAllWork() }
        assertTrue(!markerStore.isPending())
        verifyOrder {
            mediaCache.release()
            memoryCache.clear()
            diskCache.clear()
            defaultHttpCache.evictAll()
            localHttpCache.evictAll()
            approvedMediaHttpCache.evictAll()
        }
    }

    @Test
    fun `one cleanup failure does not stop later cleanup steps`() = runTest(dispatcher) {
        createManagedCacheMarkers()
        every { mediaCache.release() } throws IOException("release failed")

        try {
            resetApp()
            fail("Expected the reset failure to be reported")
        } catch (e: IOException) {
            assertEquals("release failed", e.message)
        }

        assertTrue(Constants.Cache.MANAGED_CACHE_DIRS.all { !cacheDir.resolve(it).exists() })
        assertTrue(markerStore.isPending())
        coVerify(exactly = 1) { userPreferencesRepository.clearSettings() }
        coVerify(exactly = 1) { podcastRepository.resetDatabase() }
        verify(exactly = 1) { memoryCache.clear() }
        verify(exactly = 1) { diskCache.clear() }
        verify(exactly = 1) { defaultHttpCache.evictAll() }
        verify(exactly = 1) { localHttpCache.evictAll() }
        verify(exactly = 1) { approvedMediaHttpCache.evictAll() }
    }

    @Test
    fun `settings failure leaves marker and prevents irreversible reset steps`() = runTest(dispatcher) {
        coEvery { userPreferencesRepository.clearSettings() } throws IOException("settings failed")

        try {
            resetApp()
            fail("Expected settings failure")
        } catch (error: IOException) {
            assertEquals("settings failed", error.message)
        }

        assertTrue(markerStore.isPending())
        verify(exactly = 0) { workManager.cancelAllWork() }
        coVerify(exactly = 0) { podcastRepository.resetDatabase() }
        verify(exactly = 0) { mediaCache.release() }
    }

    @Test
    fun `pending reset resumes idempotently and clears marker only after completion`() = runTest(dispatcher) {
        coEvery { userPreferencesRepository.clearSettings() } throws IOException("first attempt")
        try {
            resetApp()
            fail("Expected first reset attempt to fail")
        } catch (_: IOException) {
            assertTrue(markerStore.isPending())
        }
        coEvery { userPreferencesRepository.clearSettings() } returns Unit

        assertTrue(resetApp.resumeIfPending())

        assertTrue(!markerStore.isPending())
        assertEquals(
            listOf(Constants.FEED_UPDATE_WORK_NAME, LibraryCleanupWorker.WORK_NAME),
            scheduledWorkNames
        )
        verify(exactly = 1) { workManager.cancelAllWork() }
        coVerify(exactly = 1) { podcastRepository.resetDatabase() }
    }

    private fun createManagedCacheMarkers() {
        Constants.Cache.MANAGED_CACHE_DIRS.forEach { directoryName ->
            createMarker(cacheDir.resolve(directoryName))
        }
    }

    private fun stubScheduleOperation(
        workName: String,
        result: ListenableFuture<Operation.State.SUCCESS>
    ) {
        val operation = mockk<Operation>()
        every { operation.result } returns result
        every {
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        } returns operation
    }

    private fun createMarker(directory: File): File {
        assertTrue(directory.mkdirs() || directory.isDirectory)
        return directory.resolve("marker").apply { writeText("keep-or-delete") }
    }

    private fun clientWith(cache: Cache): OkHttpClient =
        mockk<OkHttpClient>().also { client ->
            every { client.cache } returns cache
        }
}
