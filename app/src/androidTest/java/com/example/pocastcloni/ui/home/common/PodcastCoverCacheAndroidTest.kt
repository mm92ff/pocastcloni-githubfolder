package com.example.pocastcloni.ui.home.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PodcastCoverCacheAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var cacheDirectory: File
    private lateinit var server: MockWebServer
    private var serverRunning = false

    @Before
    fun setUp() {
        cacheDirectory = context.cacheDir.resolve("podcast-cover-cache-test-${System.nanoTime()}")
        server = MockWebServer().apply { start() }
        serverRunning = true
    }

    @After
    fun tearDown() {
        if (serverRunning) server.shutdown()
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun freshLoaderUsesDiskAndThenMemoryWithoutAnotherNetworkRequest() = runBlocking {
        server.enqueue(pngResponse())
        val coverUrl = server.url("/cover.png").toString()
        val firstLoader = imageLoader()
        try {
            val networkResult = firstLoader.execute(request(coverUrl, PodcastCoverSize.GRID))

            assertTrue(networkResult is SuccessResult)
            assertEquals(DataSource.NETWORK, (networkResult as SuccessResult).dataSource)
            assertEquals(1, server.requestCount)
        } finally {
            firstLoader.shutdown()
        }

        val freshLoader = imageLoader()
        try {
            val diskResult = freshLoader.execute(request(coverUrl, PodcastCoverSize.GRID))
            assertTrue(diskResult is SuccessResult)
            assertEquals(DataSource.DISK, (diskResult as SuccessResult).dataSource)
            assertEquals(1, server.requestCount)

            server.shutdown()
            serverRunning = false

            val memoryResult = freshLoader.execute(offlineRequest(coverUrl, PodcastCoverSize.GRID))
            assertTrue(memoryResult is SuccessResult)
            assertEquals(DataSource.MEMORY_CACHE, (memoryResult as SuccessResult).dataSource)

            val otherSizeResult = freshLoader.execute(offlineRequest(coverUrl, PodcastCoverSize.LIST))
            assertTrue(otherSizeResult is SuccessResult)
            assertEquals(DataSource.DISK, (otherSizeResult as SuccessResult).dataSource)
            assertEquals(1, server.requestCount)
        } finally {
            freshLoader.shutdown()
        }
    }

    @Test
    fun changedQueryUsesANewDiskIdentityAndNetworkRequest() = runBlocking {
        server.enqueue(pngResponse())
        server.enqueue(pngResponse())
        val baseUrl = server.url("/versioned-cover.png").toString()
        val loader = imageLoader()
        try {
            val firstResult = loader.execute(request("$baseUrl?v=1", PodcastCoverSize.GRID))
            val changedResult = loader.execute(request("$baseUrl?v=2", PodcastCoverSize.GRID))

            assertTrue(firstResult is SuccessResult)
            assertTrue(changedResult is SuccessResult)
            assertEquals(DataSource.NETWORK, (firstResult as SuccessResult).dataSource)
            assertEquals(DataSource.NETWORK, (changedResult as SuccessResult).dataSource)
            assertEquals(2, server.requestCount)
        } finally {
            loader.shutdown()
        }
    }

    private fun imageLoader(): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(4 * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDirectory)
                    .maxSizeBytes(8L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

    private fun request(
        url: String,
        size: PodcastCoverSize
    ): ImageRequest = requireNotNull(PodcastCoverRequestFactory.create(context, url, size))

    private fun offlineRequest(
        url: String,
        size: PodcastCoverSize
    ): ImageRequest =
        request(url, size).newBuilder()
            .networkCachePolicy(CachePolicy.DISABLED)
            .build()

    private fun pngResponse(): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "image/png")
            .setBody(Buffer().write(TINY_PNG))

    private companion object {
        val TINY_PNG: ByteArray =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNoaGgAAAMEAYFL09IQAAAAAElFTkSuQmCC"
            )
    }
}
