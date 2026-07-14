package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DownloadWorkCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `enqueue failure is awaited and rolls queued status back`() {
        val commands = mockk<PodcastCommandPort>()
        val workManager = mockk<WorkManager>()
        val episode = episode(DownloadStatus.NOT_DOWNLOADED)
        val successfulCancel = operation(Futures.immediateFuture(Operation.SUCCESS))
        val failedEnqueue = operation(Futures.immediateFailedFuture(IOException("enqueue failed")))
        val request = slot<OneTimeWorkRequest>()
        coEvery { commands.compareAndSetDownloadStatus(any(), any(), any(), any()) } returns true
        every { workManager.cancelUniqueWork(any()) } returns successfulCancel
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), capture(request))
        } returns failedEnqueue

        assertThrows(IOException::class.java) {
            runTest { queueEpisodeDownload(workManager, commands, episode) }
        }

        assertEquals(
            mapOf("episode_id" to episode.episodeId),
            request.captured.workSpec.input.keyValueMap
        )
        coVerify {
            commands.compareAndSetDownloadStatus(
                episode.episodeId,
                listOf(DownloadStatus.QUEUED),
                DownloadStatus.NOT_DOWNLOADED,
                null
            )
        }
    }

    @Test
    fun `cancellation rereads current path before deletion`() = runTest {
        val query = mockk<PodcastQueryPort>()
        val commands = mockk<PodcastCommandPort>()
        val workManager = mockk<WorkManager>()
        val context = mockk<Context>()
        val staleFile = temporaryFolder.newFile("stale.mp3").apply { writeText("stale") }
        val currentFile = temporaryFolder.newFile("current.mp3").apply { writeText("current") }
        val original = episode(DownloadStatus.DOWNLOADING).copy(downloadPath = staleFile.absolutePath)
        val current = original.copy(downloadPath = currentFile.absolutePath)
        every { context.filesDir } returns temporaryFolder.root
        every { workManager.cancelUniqueWork(any()) } returns operation(Futures.immediateFuture(Operation.SUCCESS))
        coEvery { query.getEpisode(original.episodeId) } returns current
        coEvery { commands.compareAndSetDownloadStatusAndPath(any(), any(), any(), any(), any()) } returns true

        cancelAndDeleteEpisodeDownload(context, workManager, query, commands, original)

        assertTrue(staleFile.exists())
        assertFalse(currentFile.exists())
        coVerify {
            commands.compareAndSetDownloadStatusAndPath(
                current.episodeId,
                current.downloadStatus,
                current.downloadPath,
                DownloadStatus.NOT_DOWNLOADED,
                null
            )
        }
    }

    private fun operation(
        result: com.google.common.util.concurrent.ListenableFuture<Operation.State.SUCCESS>
    ): Operation {
        val operation = mockk<Operation>()
        every { operation.result } returns result
        return operation
    }

    private fun episode(status: DownloadStatus) =
        Episode(
            guid = "guid",
            podcastRssUrl = "https://example.com/feed.xml",
            title = "Episode",
            description = "Description",
            pubDate = null,
            link = "https://example.com/episode",
            enclosureUrl = "https://example.com/episode.mp3",
            downloadStatus = status,
            episodeId = 42L
        )
}
