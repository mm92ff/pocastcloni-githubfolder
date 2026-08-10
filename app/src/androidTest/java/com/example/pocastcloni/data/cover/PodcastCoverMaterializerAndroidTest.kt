package com.example.pocastcloni.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.di.DefaultDispatcherProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoilApi::class)
class PodcastCoverMaterializerAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var database: AppDatabase
    private lateinit var store: PodcastCoverThumbnailStore
    private lateinit var diskCache: DiskCache
    private lateinit var materializer: PodcastCoverMaterializer
    private lateinit var feedUrl: String

    @Before
    fun setUp() {
        server.start()
        feedUrl = server.url("/feed.xml").toString()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = PodcastCoverThumbnailStore(context, DefaultDispatcherProvider())
        diskCache =
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(TEST_DISK_CACHE))
                .maxSizeBytes(10L * 1024L * 1024L)
                .build()
        materializer =
            PodcastCoverMaterializer(
                database = database,
                podcastDao = database.podcastDao(),
                coverStateDao = database.podcastCoverStateDao(),
                thumbnailStore = store,
                refreshPolicy = PodcastCoverRefreshPolicy(),
                clock = SystemPodcastCoverClock(),
                fileLifecycleLock = PodcastCoverFileLifecycleLock(),
                imageDiskCache = diskCache,
                imageClient = OkHttpClient(),
                dispatcherProvider = DefaultDispatcherProvider()
            )
    }

    @After
    fun tearDown() = runBlocking {
        store.deletePodcastFiles(feedUrl)
        store.deletePodcastFiles(FOREIGN_FEED_URL)
        diskCache.clear()
        context.cacheDir.resolve(TEST_DISK_CACHE).deleteRecursively()
        database.close()
        server.shutdown()
    }

    @Test
    fun persistentCoverSurvivesCacheCleanupAndChangesOnlyWhenForced() = runBlocking {
        val firstUrl = server.url("/cover.png").toString()
        val secondUrl = server.url("/cover-2.png").toString()
        database.podcastDao().insertPodcast(
            PodcastEntity(
                rssUrl = feedUrl,
                title = "Test podcast",
                description = "Description",
                imageUrl = firstUrl,
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
        server.enqueue(imageResponse(Color.RED, "first-etag"))

        val first = materializer.materialize(feedUrl)
        assertTrue(first is PodcastCoverMaterializationResult.Available)
        val firstState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))
        assertNotNull(store.validFile(firstState.thumbnailFileName))
        assertEquals(1, server.requestCount)

        diskCache.clear()
        val afterCacheCleanup = materializer.materialize(feedUrl)
        assertTrue(afterCacheCleanup is PodcastCoverMaterializationResult.Available)
        assertEquals(1, server.requestCount)

        database.podcastCoverStateDao().observeFeedCandidate(
            feedUrl,
            secondUrl,
            System.currentTimeMillis()
        )
        val duringCooldown = materializer.materialize(feedUrl)
        assertTrue(duringCooldown is PodcastCoverMaterializationResult.Available)
        assertEquals(firstState.thumbnailFileName, database.podcastCoverStateDao().getState(feedUrl)?.thumbnailFileName)
        assertEquals(1, server.requestCount)

        server.enqueue(imageResponse(Color.BLUE, "second-etag"))
        val forced = materializer.materialize(feedUrl, force = true)
        assertTrue(forced is PodcastCoverMaterializationResult.Available)
        val secondState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))
        assertEquals(secondUrl, secondState.activeSourceUrl)
        assertEquals(2L, secondState.thumbnailRevision)
        assertNotEquals(firstState.thumbnailFileName, secondState.thumbnailFileName)
        assertNotNull(store.validFile(secondState.thumbnailFileName))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun concurrentFirstLoadsCoalesceToOneNetworkRequest() = runBlocking {
        val sourceUrl = server.url("/cover.png").toString()
        insertPodcast(sourceUrl)
        server.enqueue(imageResponse(Color.RED, "first-etag"))

        val results = coroutineScope {
            List(4) { async { materializer.materialize(feedUrl) } }.awaitAll()
        }

        assertTrue(results.all { it is PodcastCoverMaterializationResult.Available })
        assertEquals(1, server.requestCount)
        assertEquals(1L, database.podcastCoverStateDao().getState(feedUrl)?.thumbnailRevision)
    }

    @Test
    fun forcedSameUrlValidationUsesValidatorsAnd304KeepsRevision() = runBlocking {
        val sourceUrl = server.url("/cover.png").toString()
        insertPodcast(sourceUrl)
        server.enqueue(
            imageResponse(Color.RED, "first-etag")
                .setHeader("Last-Modified", LAST_MODIFIED)
        )
        materializer.materialize(feedUrl)
        val firstState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(304))

        val result = materializer.materialize(feedUrl, force = true)
        val validationRequest = server.takeRequest()
        val afterValidation = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))

        assertTrue(result is PodcastCoverMaterializationResult.Available)
        assertEquals(
            PodcastCoverMaterializationSource.NOT_MODIFIED,
            (result as PodcastCoverMaterializationResult.Available).source
        )
        assertEquals("first-etag", validationRequest.getHeader("If-None-Match"))
        assertEquals(LAST_MODIFIED, validationRequest.getHeader("If-Modified-Since"))
        assertEquals(firstState.thumbnailFileName, afterValidation.thumbnailFileName)
        assertEquals(firstState.thumbnailRevision, afterValidation.thumbnailRevision)
    }

    @Test
    fun invalidReplacementIsPermanentAndRetainsPublishedCover() = runBlocking {
        val firstUrl = server.url("/cover.png").toString()
        val secondUrl = server.url("/invalid.png").toString()
        insertPodcast(firstUrl)
        server.enqueue(imageResponse(Color.RED, "first-etag"))
        materializer.materialize(feedUrl)
        val firstState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))
        val firstBytes = requireNotNull(store.readArtworkBytes(firstState.thumbnailFileName))
        database.podcastCoverStateDao().observeFeedCandidate(feedUrl, secondUrl, System.currentTimeMillis())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(byteArrayOf(1, 2, 3, 4)))
        )

        val result = materializer.materialize(feedUrl, force = true)
        val failedState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))

        assertTrue(result is PodcastCoverMaterializationResult.PermanentFailure)
        assertEquals(firstState.thumbnailFileName, failedState.thumbnailFileName)
        assertEquals(firstState.thumbnailRevision, failedState.thumbnailRevision)
        assertArrayEquals(firstBytes, store.readArtworkBytes(failedState.thumbnailFileName))
    }

    @Test
    fun displayResolutionRejectsAValidFileOwnedByAnotherPodcast() = runBlocking {
        val sourceUrl = server.url("/cover.png").toString()
        insertPodcast(sourceUrl)
        server.enqueue(imageResponse(Color.RED, "first-etag"))
        materializer.materialize(feedUrl)
        val expectedState = requireNotNull(database.podcastCoverStateDao().getState(feedUrl))
        val foreign =
            store.publish(
                FOREIGN_FEED_URL,
                ByteArrayInputStream(createPng(Color.BLUE))
            )

        val result = materializer.resolveForDisplay(feedUrl, sourceUrl, foreign.fileName)

        assertTrue(result is PodcastCoverMaterializationResult.Available)
        assertEquals(
            expectedState.thumbnailFileName,
            (result as PodcastCoverMaterializationResult.Available).file.name
        )
        assertNotEquals(foreign.fileName, result.file.name)
        assertEquals(1, server.requestCount)
    }

    private suspend fun insertPodcast(sourceUrl: String) {
        database.podcastDao().insertPodcast(
            PodcastEntity(
                rssUrl = feedUrl,
                title = "Test podcast",
                description = "Description",
                imageUrl = sourceUrl,
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
    }

    private fun imageResponse(
        color: Int,
        eTag: String
    ): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "image/png")
            .setHeader("ETag", eTag)
            .setBody(Buffer().write(createPng(color)))

    private fun createPng(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private companion object {
        const val TEST_DISK_CACHE = "podcast-cover-materializer-test"
        const val LAST_MODIFIED = "Mon, 10 Aug 2026 12:00:00 GMT"
        const val FOREIGN_FEED_URL = "https://instrumentation.example/foreign-feed.xml"
    }
}
