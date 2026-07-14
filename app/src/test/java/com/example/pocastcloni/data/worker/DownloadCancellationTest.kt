package com.example.pocastcloni.data.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.util.downloadWorkName
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DownloadCancellationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        DownloadWorkStateCoordinator.clearAttempt(EPISODE_ID)
    }

    @After
    fun tearDown() {
        DownloadWorkStateCoordinator.clearAttempt(EPISODE_ID)
    }

    @Test
    fun `terminal cancellation finishes reset and cleanup after parent is cancelled`() = runTest {
        val workManager = workManager(WorkInfo.State.CANCELLED)
        val query = mockk<PodcastQueryPort>(relaxed = true)
        val commands = mockk<PodcastCommandPort>()
        val staging = stagingFiles()
        val publication = FakePublication()
        val transitionStarted = CompletableDeferred<Unit>()
        val finishTransition = CompletableDeferred<Unit>()
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } coAnswers {
            transitionStarted.complete(Unit)
            finishTransition.await()
            true
        }
        val job =
            launch {
                handleDownloadWorkerCancellation(
                    workManager,
                    WORK_ID,
                    EPISODE_ID,
                    query,
                    commands,
                    staging,
                    publication
                )
            }
        transitionStarted.await()
        job.cancel()
        finishTransition.complete(Unit)
        job.cancelAndJoin()

        coVerify {
            commands.compareAndSetDownloadStatus(
                EPISODE_ID,
                listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.DOWNLOADED),
                DownloadStatus.NOT_DOWNLOADED,
                null
            )
        }
        assertTrue(publication.cleaned)
        assertFalse(staging.partFile.exists())
        assertFalse(staging.metadataFile.exists())
    }

    @Test
    fun `constraint stop queues work and preserves resumable staging`() = runTest {
        val workManager = workManager(WorkInfo.State.ENQUEUED)
        val query = mockk<PodcastQueryPort>(relaxed = true)
        val commands = mockk<PodcastCommandPort>()
        val staging = stagingFiles()
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } returns true

        val retained =
            handleDownloadWorkerCancellation(
                workManager,
                WORK_ID,
                EPISODE_ID,
                query,
                commands,
                staging,
                publication = null
            )

        assertTrue(retained)
        assertTrue(staging.partFile.exists())
        assertTrue(staging.metadataFile.exists())
        coVerify {
            commands.compareAndSetDownloadStatus(
                EPISODE_ID,
                listOf(DownloadStatus.DOWNLOADING),
                DownloadStatus.QUEUED,
                null
            )
        }
    }

    @Test
    fun `new committed attempt survives old cancellation cleanup for same logical file`() = runTest {
        val query = mockk<PodcastQueryPort>(relaxed = true)
        val commands = mockk<PodcastCommandPort>()
        val workManager = mockk<WorkManager>()
        val staging = stagingFiles()
        val targetDirectory = temporaryFolder.newFolder("replacement-download")
        val oldPublication = legacyPublication("old-publication.part", "old", targetDirectory, WORK_ID)
        var databasePath: String? = null
        commitDownloadPublication(oldPublication, { databasePath = it; true }, {})
        val operation = successfulOperation()
        val newRequest = slot<OneTimeWorkRequest>()
        val oldWork = workInfo(WORK_ID, WorkInfo.State.CANCELLED)
        val newWork = mockk<WorkInfo>()
        every { workManager.cancelUniqueWork(any()) } returns operation
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), capture(newRequest))
        } returns operation
        every { newWork.id } answers { newRequest.captured.id }
        every { newWork.state } returns WorkInfo.State.SUCCEEDED
        every { workManager.getWorkInfoById(WORK_ID) } returns Futures.immediateFuture(oldWork)
        every { workManager.getWorkInfosForUniqueWork(downloadWorkName(EPISODE_ID)) } answers {
            Futures.immediateFuture(listOf(oldWork, newWork))
        }
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } returns true

        assertTrue(queueEpisodeDownload(workManager, commands, episode()))
        val newPublication =
            legacyPublication("new-publication.part", "new", targetDirectory, newRequest.captured.id)
        commitDownloadPublication(newPublication, { databasePath = it; true }, {})
        val retained =
            handleDownloadWorkerCancellation(
                workManager,
                WORK_ID,
                EPISODE_ID,
                query,
                commands,
                staging,
                oldPublication
            )

        assertTrue(retained)
        assertTrue(staging.partFile.exists())
        assertTrue(staging.metadataFile.exists())
        assertEquals(newPublication.path, databasePath)
        assertEquals("new", File(newPublication.path).readText())
        coVerify(exactly = 1) {
            commands.compareAndSetDownloadStatus(
                EPISODE_ID,
                listOf(DownloadStatus.NOT_DOWNLOADED),
                DownloadStatus.QUEUED,
                null
            )
        }
    }

    private suspend fun legacyPublication(
        stagedFileName: String,
        contents: String,
        targetDirectory: File,
        attemptId: UUID
    ): PendingDownloadPublication =
        prepareLegacyPublicFilePublication(
            stagedFile = temporaryFolder.newFile(stagedFileName).apply { writeText(contents) },
            targetDirectory = targetDirectory,
            fileName = LOGICAL_FILE_NAME,
            attemptId = attemptId
        )

    private fun workManager(state: WorkInfo.State): WorkManager {
        val workInfo = workInfo(WORK_ID, state)
        return mockk {
            every { getWorkInfoById(WORK_ID) } returns Futures.immediateFuture(workInfo)
            every { getWorkInfosForUniqueWork(downloadWorkName(EPISODE_ID)) } returns
                Futures.immediateFuture(listOf(workInfo))
        }
    }

    private fun workInfo(
        id: UUID,
        state: WorkInfo.State
    ): WorkInfo =
        mockk<WorkInfo>().also { workInfo ->
            every { workInfo.id } returns id
            every { workInfo.state } returns state
        }

    private fun successfulOperation(): Operation =
        mockk<Operation>().also { operation ->
            every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        }

    private fun episode() =
        Episode(
            guid = "guid",
            podcastRssUrl = "https://example.com/feed.xml",
            title = "Episode",
            description = "Description",
            pubDate = null,
            link = "https://example.com/episode",
            enclosureUrl = "https://example.com/episode.mp3",
            downloadStatus = DownloadStatus.NOT_DOWNLOADED,
            episodeId = EPISODE_ID
        )

    private fun stagingFiles(): DownloadStagingFiles {
        val directory = temporaryFolder.newFolder()
        return DownloadStagingFiles(
            directory.resolve("$EPISODE_ID.part").apply { writeText("partial") },
            directory.resolve("$EPISODE_ID.meta").apply { writeText("metadata") }
        )
    }

    private class FakePublication : PendingDownloadPublication {
        override val path: String = "/pending.mp3"
        override val totalBytes: Long = 7L
        var cleaned = false

        override fun makeVisible() = Unit

        override fun cleanup() {
            cleaned = true
        }
    }

    private companion object {
        const val LOGICAL_FILE_NAME = "Podcast_Episode.mp3"
        val WORK_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000007")
        const val EPISODE_ID = 7L
    }
}
