package com.example.pocastcloni.data.worker

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pocastcloni.PocastApplication
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.repository.InstallationState
import com.example.pocastcloni.data.repository.InstallationStateProvider
import com.example.pocastcloni.data.repository.UserPreferencesRepositoryImpl
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadWorkerLifecycleAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.podcastDao() }
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val userPreferences by lazy {
        UserPreferencesRepositoryImpl(
            context,
            InstallationStateProvider { InstallationState.FRESH }
        )
    }
    private val downloadsDirectory: File
        get() = context.filesDir.resolve(Constants.DOWNLOADS_DIR)

    private lateinit var server: MockWebServer
    private var episodeId: Long? = null
    private var originalSaveToDownloadsFolder: Boolean? = null

    @Before
    fun setUp() = runBlocking {
        (context.applicationContext as PocastApplication)
            .appInitializer
            .awaitStartupCompletion()
        cancelAndAwaitDownloadWorkers()
        dao.deleteAllPodcasts()
        downloadsDirectory.deleteRecursively()
        originalSaveToDownloadsFolder = userPreferences.userSettingsFlow.first().saveToDownloadsFolder
        userPreferences.updateSaveToDownloadsFolder(false)
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        runBlocking {
            try {
                cancelAndAwaitDownloadWorkers()
                episodeId?.let(DownloadWorkStateCoordinator::clearAttempt)
                dao.deleteAllPodcasts()
                downloadsDirectory.deleteRecursively()
                originalSaveToDownloadsFolder?.let {
                    userPreferences.updateSaveToDownloadsFolder(it)
                }
            } finally {
                if (::server.isInitialized) server.shutdown()
            }
        }
    }

    @Test
    @Suppress("LongMethod")
    fun interruptedTransferRetriesWithRangeAndPublishesPrivateFile() = runBlocking {
        val expectedBytes = ByteArray(PAYLOAD_BYTES) { index -> (index % PAYLOAD_PATTERN_SIZE).toByte() }
        val rangeDispatcher = RangeDispatcher(expectedBytes)
        server.dispatcher = rangeDispatcher
        val feedUrl = server.url("/feed.xml").toString()
        val enclosureUrl = server.url("/episode.mp3").toString()

        dao.insertPodcast(
            PodcastEntity(
                rssUrl = feedUrl,
                title = "Lifecycle Podcast",
                description = "Download lifecycle test",
                imageUrl = "",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
        dao.insertEpisode(
            EpisodeEntity(
                guid = "download-lifecycle",
                podcastRssUrl = feedUrl,
                title = "Lifecycle Episode",
                description = "Download lifecycle test",
                pubDate = null,
                link = enclosureUrl,
                enclosureUrl = enclosureUrl,
                downloadStatus = DownloadStatus.QUEUED
            )
        )
        val storedEpisode = requireNotNull(dao.getEpisodeByFeedAndGuid(feedUrl, "download-lifecycle"))
        episodeId = storedEpisode.episodeId
        val stagingFiles = downloadStagingFiles(context.filesDir, storedEpisode.episodeId)

        val workRequest = enqueueDownload(storedEpisode.episodeId)

        val observedPartialBytes = awaitPartialTransfer(stagingFiles, expectedBytes.size.toLong())
        awaitEpisodeDownloadStatus(storedEpisode.episodeId, DownloadStatus.QUEUED)
        awaitWorkState(workRequest.id, WorkInfo.State.ENQUEUED)
        assertEquals(
            DownloadStatus.QUEUED,
            dao.getEpisodeById(storedEpisode.episodeId)?.downloadStatus
        )

        val completedWork = awaitWorkState(workRequest.id, WorkInfo.State.SUCCEEDED)
        val firstHttpRequest = takeRequestForPath(EPISODE_PATH, "initial download request")
        val resumedHttpRequest = takeRequestForPath(EPISODE_PATH, "resumed download request")
        val rangeOffset = requireRangeOffset(resumedHttpRequest)

        assertNull(firstHttpRequest.getHeader(HEADER_RANGE))
        assertTrue(
            "Resume offset $rangeOffset did not include the observed $observedPartialBytes bytes",
            rangeOffset >= observedPartialBytes
        )
        assertTrue("Resume offset must be inside the payload", rangeOffset < expectedBytes.size)
        assertEquals(ETAG, resumedHttpRequest.getHeader(HEADER_IF_RANGE))
        assertEquals(2, rangeDispatcher.episodeRequestCount)

        val completedEpisode = requireNotNull(dao.getEpisodeById(storedEpisode.episodeId))
        assertEquals(DownloadStatus.DOWNLOADED, completedEpisode.downloadStatus)
        val publishedPath = requireNotNull(completedEpisode.downloadPath)
        assertEquals(
            publishedPath,
            completedWork.outputData.getString(Constants.DOWNLOAD_WORKER_OUTPUT_PATH)
        )
        val publishedFile = File(publishedPath)
        assertTrue(
            "Expected a private download under $downloadsDirectory but was $publishedFile",
            publishedFile.canonicalPath.startsWith(downloadsDirectory.canonicalPath + File.separator)
        )
        assertTrue("Published download is not a readable file: $publishedFile", publishedFile.isFile)
        assertTrue("Published download is not readable: $publishedFile", publishedFile.canRead())
        assertEquals(expectedBytes.size.toLong(), publishedFile.length())
        assertArrayEquals(expectedBytes, publishedFile.readBytes())
        assertFalse("Staging part should be removed after publication", stagingFiles.partFile.exists())
        assertFalse("Resume metadata should be removed after publication", stagingFiles.metadataFile.exists())
    }

    private suspend fun enqueueDownload(
        episodeId: Long
    ): OneTimeWorkRequest = DownloadWorkStateCoordinator.withLock {
        val request = createDownloadRequest(episodeId)
        val operation =
            workManager.enqueueUniqueWork(
                downloadWorkName(episodeId),
                ExistingWorkPolicy.KEEP,
                request
            )
        DownloadWorkStateCoordinator.recordEnqueuedAttempt(episodeId, request.id)
        operation.result.await()
        request
    }

    private fun createDownloadRequest(episodeId: Long): OneTimeWorkRequest {
        val workName = downloadWorkName(episodeId)
        return OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(Constants.DOWNLOAD_WORKER_EPISODE_ID to episodeId))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                TEST_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(Constants.DOWNLOAD_WORKER_TAG)
            .addTag(workName)
            .build()
    }

    private fun awaitPartialTransfer(
        stagingFiles: DownloadStagingFiles,
        totalBytes: Long
    ): Long {
        val deadline = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS
        var observedBytes = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            observedBytes = stagingFiles.partFile.takeIf { it.isFile }?.length() ?: 0L
            if (
                stagingFiles.metadataFile.isFile &&
                observedBytes in MIN_OBSERVED_PARTIAL_BYTES until totalBytes
            ) {
                return observedBytes
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail(
            "Timed out waiting for a partial transfer; bytes=$observedBytes, " +
                "metadata=${stagingFiles.metadataFile.exists()}, work=${currentEpisodeWorkStates()}"
        )
        throw AssertionError("Unreachable")
    }

    private fun awaitWorkState(
        workId: UUID,
        expectedState: WorkInfo.State
    ): WorkInfo {
        val deadline = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS
        var workInfo = queryWork(workId)
        while (workInfo?.state != expectedState && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            workInfo = queryWork(workId)
        }
        if (workInfo?.state == expectedState) return workInfo
        fail(
            "Timed out waiting for $workId to reach $expectedState; " +
                "last=${workInfo?.state}, episodeWork=${currentEpisodeWorkStates()}"
        )
        throw AssertionError("Unreachable")
    }

    private suspend fun awaitEpisodeDownloadStatus(
        episodeId: Long,
        expectedStatus: DownloadStatus
    ) {
        val deadline = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS
        var status = dao.getEpisodeById(episodeId)?.downloadStatus
        while (status != expectedStatus && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            status = dao.getEpisodeById(episodeId)?.downloadStatus
        }
        if (status != expectedStatus) {
            fail("Timed out waiting for episode $episodeId to reach $expectedStatus; last=$status")
        }
    }

    private fun queryWork(workId: UUID): WorkInfo? =
        workManager.getWorkInfoById(workId).get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private suspend fun cancelAndAwaitDownloadWorkers() {
        workManager.cancelAllWorkByTag(Constants.DOWNLOAD_WORKER_TAG).result.await()
        val deadline = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS
        var activeWork = queryTaggedDownloadWork().filterNot { it.state.isFinished }
        while (activeWork.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            activeWork = queryTaggedDownloadWork().filterNot { it.state.isFinished }
        }
        assertTrue(
            "Timed out waiting for cancelled download workers; active=" +
                activeWork.joinToString { "${it.id}:${it.state}" },
            activeWork.isEmpty()
        )
    }

    private fun queryTaggedDownloadWork(): List<WorkInfo> =
        workManager
            .getWorkInfosByTag(Constants.DOWNLOAD_WORKER_TAG)
            .get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun currentEpisodeWorkStates(): String {
        val currentEpisodeId = episodeId ?: return "episode not inserted"
        return workManager
            .getWorkInfosForUniqueWork(downloadWorkName(currentEpisodeId))
            .get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .joinToString { "${it.id}:${it.state}" }
    }

    private fun takeRequestForPath(
        path: String,
        description: String
    ): RecordedRequest {
        val deadline = SystemClock.elapsedRealtime() + HTTP_REQUEST_TIMEOUT_SECONDS * 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val remainingMillis = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val request = server.takeRequest(remainingMillis, TimeUnit.MILLISECONDS) ?: break
            if (request.requestUrl?.encodedPath == path) return request
        }
        throw AssertionError("Timed out waiting for $description at $path")
    }

    private fun requireRangeOffset(request: RecordedRequest): Long {
        val range = request.getHeader(HEADER_RANGE)
            ?: throw AssertionError("Resumed request did not include a Range header")
        return RANGE_PATTERN.matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw AssertionError("Unexpected Range header: $range")
    }

    private class RangeDispatcher(
        private val expectedBytes: ByteArray
    ) : Dispatcher() {
        private var initialRequestServed = false
        var episodeRequestCount: Int = 0
            private set

        @Synchronized
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.requestUrl?.encodedPath == FEED_PATH) return feedResponse()
            if (request.requestUrl?.encodedPath != EPISODE_PATH) {
                return MockResponse().setResponseCode(HTTP_NOT_FOUND)
            }
            episodeRequestCount += 1
            val range = request.getHeader(HEADER_RANGE)
            if (!initialRequestServed) {
                initialRequestServed = true
                return interruptedFullResponse()
            }
            if (range == null) return MockResponse().setResponseCode(HTTP_BAD_REQUEST)
            val offset = RANGE_PATTERN.matchEntire(range)?.groupValues?.get(1)?.toIntOrNull()
                ?: return MockResponse().setResponseCode(HTTP_BAD_REQUEST)
            if (offset <= 0 || offset >= expectedBytes.size) {
                return MockResponse().setResponseCode(HTTP_RANGE_NOT_SATISFIABLE)
            }
            val remainingBytes = expectedBytes.copyOfRange(offset, expectedBytes.size)
            return MockResponse()
                .setResponseCode(HTTP_PARTIAL_CONTENT)
                .setHeader(HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_STORE)
                .setHeader(HEADER_ETAG, ETAG)
                .setHeader(
                    HEADER_CONTENT_RANGE,
                    "bytes $offset-${expectedBytes.lastIndex}/${expectedBytes.size}"
                )
                .setBody(Buffer().write(remainingBytes))
        }

        private fun interruptedFullResponse(): MockResponse =
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader(HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_STORE)
                .setHeader(HEADER_ETAG, ETAG)
                .setBody(Buffer().write(expectedBytes))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

        private fun feedResponse(): MockResponse =
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", "application/rss+xml")
                .setBody(MINIMAL_FEED)
    }

    private companion object {
        const val PAYLOAD_BYTES = 512 * 1024
        const val PAYLOAD_PATTERN_SIZE = 251
        const val MIN_OBSERVED_PARTIAL_BYTES = 1L
        const val TEST_BACKOFF_SECONDS = 10L
        const val STATE_TIMEOUT_MS = 45_000L
        const val POLL_INTERVAL_MS = 50L
        const val WORK_QUERY_TIMEOUT_SECONDS = 5L
        const val HTTP_REQUEST_TIMEOUT_SECONDS = 5L
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HEADER_RANGE = "Range"
        const val HEADER_IF_RANGE = "If-Range"
        const val HEADER_ETAG = "ETag"
        const val HEADER_CONTENT_RANGE = "Content-Range"
        const val HEADER_CACHE_CONTROL = "Cache-Control"
        const val CACHE_CONTROL_NO_STORE = "no-store"
        const val ETAG = "\"download-lifecycle-v1\""
        const val FEED_PATH = "/feed.xml"
        const val EPISODE_PATH = "/episode.mp3"
        const val MINIMAL_FEED =
            "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>" +
                "<title>Lifecycle Podcast</title></channel></rss>"
        val RANGE_PATTERN = Regex("bytes=(\\d+)-")
    }
}
