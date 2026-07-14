package com.example.pocastcloni.data.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.util.downloadWorkName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResumableDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val metadataStore = DownloadResumeMetadataStore(ObjectMapper().registerKotlinModule())

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `valid range response appends only missing bytes`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "hello ", strongETag = "\"v1\"", totalBytes = 11L)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 6-10/11")
                .setBody("world")
        )

        val result = downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("hello world", result.partFile.readText())
        val request = server.takeRequest()
        assertEquals("bytes=6-", request.getHeader("Range"))
        assertEquals("\"v1\"", request.getHeader("If-Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
    }

    @Test
    fun `server without range support replaces partial content`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "old", strongETag = "\"v1\"", totalBytes = 10L)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v2\"").setBody("replacement"))

        downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("replacement", staging.partFile.readText())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `range rejection performs one clean full restart`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "old", strongETag = "\"v1\"", totalBytes = 10L)
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v2\"").setBody("fresh"))

        downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("fresh", staging.partFile.readText())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `changed validator and malformed range cannot be appended`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "old", strongETag = "\"v1\"", totalBytes = 8L)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"changed\"")
                .setHeader("Content-Range", "bytes nope")
                .setBody("bytes")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("clean"))

        downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("clean", staging.partFile.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `changed content range total with matching validator restarts once`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "old", strongETag = "\"v1\"", totalBytes = 8L)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 3-7/9")
                .setBody("bytes")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("clean"))

        downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("clean", staging.partFile.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `unknown content range total with persisted total restarts once`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "old", strongETag = "\"v1\"", totalBytes = 8L)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 3-7/*")
                .setBody("bytes")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("clean"))

        downloader().download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }

        assertEquals("clean", staging.partFile.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `terminal cancellation after active transfer deletes resumable staging`() = runBlocking {
        val transfer = cancelActiveTransfer()
        val query = mockk<PodcastQueryPort>(relaxed = true)
        val commands = mockk<PodcastCommandPort>()
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } returns true

        val retained =
            handleDownloadWorkerCancellation(
                workManager(WorkInfo.State.CANCELLED),
                WORK_ID,
                EPISODE_ID,
                query,
                commands,
                transfer.stagingFiles,
                publication = null
            )

        assertTrue(transfer.callCancelled.await(5, TimeUnit.SECONDS))
        assertFalse(retained)
        assertFalse(transfer.stagingFiles.partFile.exists())
        assertFalse(transfer.stagingFiles.metadataFile.exists())
        coVerify {
            commands.compareAndSetDownloadStatus(
                EPISODE_ID,
                listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.DOWNLOADED),
                DownloadStatus.NOT_DOWNLOADED,
                null
            )
        }
    }

    @Test
    fun `constraint cancellation after active transfer preserves resumable staging`() = runBlocking {
        val transfer = cancelActiveTransfer()
        val query = mockk<PodcastQueryPort>(relaxed = true)
        val commands = mockk<PodcastCommandPort>()
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } returns true

        val retained =
            handleDownloadWorkerCancellation(
                workManager(WorkInfo.State.ENQUEUED),
                WORK_ID,
                EPISODE_ID,
                query,
                commands,
                transfer.stagingFiles,
                publication = null
            )

        assertTrue(transfer.callCancelled.await(5, TimeUnit.SECONDS))
        assertTrue(retained)
        assertTrue(transfer.stagingFiles.partFile.exists())
        assertTrue(transfer.stagingFiles.metadataFile.exists())
        coVerify {
            commands.compareAndSetDownloadStatus(
                EPISODE_ID,
                listOf(DownloadStatus.DOWNLOADING),
                DownloadStatus.QUEUED,
                null
            )
        }
    }

    private suspend fun cancelActiveTransfer(): CancelledTransfer = coroutineScope {
        val callCancelled = CountDownLatch(1)
        val client =
            OkHttpClient.Builder()
                .eventListenerFactory {
                    object : EventListener() {
                        override fun callFailed(call: Call, ioe: IOException) {
                            if (call.isCanceled()) callCancelled.countDown()
                        }
                    }
                }
                .build()
        val url = server.url("/slow.mp3").toString()
        val staging = stagingFiles()
        server.enqueue(
            MockResponse()
                .setHeader("ETag", "\"slow\"")
                .setBody("slow response body")
                .throttleBody(1, 1, TimeUnit.SECONDS)
        )

        val job = launch(Dispatchers.Default) {
            downloader(client).download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }
        }
        server.takeRequest(5, TimeUnit.SECONDS)
        withTimeout(5_000L) {
            while (!staging.partFile.exists()) delay(10L)
        }
        job.cancelAndJoin()

        assertTrue(staging.partFile.exists())
        assertTrue(staging.metadataFile.exists())
        CancelledTransfer(staging, callCancelled)
    }

    @Test
    fun `resumed transfer reserves only remaining known bytes`() = runTest {
        val url = server.url("/episode.mp3").toString()
        val staging = stagingFiles()
        seedPartial(staging, url, "12345", strongETag = "\"v1\"", totalBytes = 10L)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 5-9/10")
                .setBody("67890")
        )

        downloader(reserveBytes = 0L).download(url, staging, availableBytes = { 5L }) { _, _ -> }

        assertEquals("1234567890", staging.partFile.readText())
    }

    @Test
    fun `known response above maximum fails before writing`() {
        val url = server.url("/large.mp3").toString()
        val staging = stagingFiles()
        server.enqueue(MockResponse().setBody("123456"))

        assertThrows(DownloadSizeLimitException::class.java) {
            runTest {
                downloader(maxBytes = 5L).download(url, staging, availableBytes = { Long.MAX_VALUE }) { _, _ -> }
            }
        }
    }

    @Test
    fun `unknown length rechecks storage periodically`() = runTest {
        val url = server.url("/chunked.mp3").toString()
        val staging = stagingFiles()
        val storageChecks = AtomicInteger()
        server.enqueue(MockResponse().setChunkedBody("1234567890", 2))

        downloader(reserveBytes = 0L).download(
            url,
            staging,
            availableBytes = {
                storageChecks.incrementAndGet()
                Long.MAX_VALUE
            }
        ) { _, _ -> }

        assertTrue(storageChecks.get() >= 3)
        assertEquals("1234567890", staging.partFile.readText())
    }

    private fun downloader(
        client: OkHttpClient = OkHttpClient(),
        maxBytes: Long = 1_024L,
        reserveBytes: Long = 0L
    ) =
        ResumableDownload(
            client = client,
            metadataStore = metadataStore,
            maxBytes = maxBytes,
            storageReserveBytes = reserveBytes,
            storageRecheckIntervalBytes = 4L
        )

    private fun stagingFiles(): DownloadStagingFiles {
        val directory = temporaryFolder.newFolder()
        return DownloadStagingFiles(File(directory, "$EPISODE_ID.part"), File(directory, "$EPISODE_ID.meta"))
    }

    private fun workManager(state: WorkInfo.State): WorkManager {
        val workInfo = mockk<WorkInfo>()
        every { workInfo.id } returns WORK_ID
        every { workInfo.state } returns state
        return mockk {
            every { getWorkInfoById(WORK_ID) } returns Futures.immediateFuture(workInfo)
            every { getWorkInfosForUniqueWork(downloadWorkName(EPISODE_ID)) } returns
                Futures.immediateFuture(listOf(workInfo))
        }
    }

    private fun seedPartial(
        staging: DownloadStagingFiles,
        url: String,
        body: String,
        strongETag: String?,
        totalBytes: Long
    ) {
        staging.partFile.writeText(body)
        metadataStore.write(
            staging.metadataFile,
            DownloadResumeMetadata(url, strongETag, null, totalBytes)
        )
    }

    private data class CancelledTransfer(
        val stagingFiles: DownloadStagingFiles,
        val callCancelled: CountDownLatch
    )

    private companion object {
        val WORK_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000007")
        const val EPISODE_ID = 7L
    }
}
