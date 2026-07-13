package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache as MediaCache
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.Constants
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var externalDownloadsDir: File
    private lateinit var resetApp: ResetAppUseCase

    @Before
    fun setUp() {
        cacheDir = temporaryFolder.newFolder("cache")
        filesDir = temporaryFolder.newFolder("files")
        externalDownloadsDir = temporaryFolder.newFolder("external", "MediaStore", "Downloads")
        every { context.cacheDir } returns cacheDir
        every { imageLoader.memoryCache } returns memoryCache
        every { imageLoader.diskCache } returns diskCache
        mediaCacheProvider.getCacheOrNull()
        resetApp =
            ResetAppUseCase(
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                mediaCacheProvider = mediaCacheProvider,
                imageLoader = imageLoader,
                okHttpClient = okHttpClient,
                localNetworkClient = localNetworkClient,
                approvedMediaClient = approvedMediaClient,
                dispatcherProvider = dispatcherProvider,
                context = context
            )
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
        coVerify(exactly = 1) { userPreferencesRepository.clearSettings() }
        coVerify(exactly = 1) { podcastRepository.resetDatabase() }
        verify(exactly = 1) { memoryCache.clear() }
        verify(exactly = 1) { diskCache.clear() }
        verify(exactly = 1) { defaultHttpCache.evictAll() }
        verify(exactly = 1) { localHttpCache.evictAll() }
        verify(exactly = 1) { approvedMediaHttpCache.evictAll() }
    }

    private fun createManagedCacheMarkers() {
        Constants.Cache.MANAGED_CACHE_DIRS.forEach { directoryName ->
            createMarker(cacheDir.resolve(directoryName))
        }
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
