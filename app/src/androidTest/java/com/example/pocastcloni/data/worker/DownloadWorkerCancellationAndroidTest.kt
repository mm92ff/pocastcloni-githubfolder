package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.ItunesResponse
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.data.repository.PodcastRepositoryImpl
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadWorkerCancellationAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.podcastDao() }
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val repository by lazy {
        PodcastRepositoryImpl(
            podcastDao = dao,
            itunesSearchApi =
            object : ItunesSearchApi {
                override suspend fun searchPodcasts(term: String) =
                    ItunesResponse(resultCount = 0, results = emptyList())
            },
            dispatcherProvider = DefaultDispatcherProvider(),
            context = context
        )
    }

    @Before
    fun setUp() {
        runBlocking {
            dao.deleteAllPodcasts()
            context.filesDir.resolve(Constants.DOWNLOADS_DIR).deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            workManager.cancelAllWorkByTag(TEST_TAG).result.await()
            dao.deleteAllPodcasts()
            context.filesDir.resolve(Constants.DOWNLOADS_DIR).deleteRecursively()
        }
    }

    @Test
    fun workManagerCancellationEquivalentResetsStateAndCleansTargets() = runBlocking {
        dao.insertPodcast(PodcastEntity(FEED_URL, "Podcast", "Description", ""))
        dao.insertEpisode(
            EpisodeEntity(
                guid = "episode",
                podcastRssUrl = FEED_URL,
                title = "Episode",
                description = "Description",
                pubDate = null,
                link = "https://example.com/episode",
                enclosureUrl = "https://example.com/episode.mp3",
                downloadStatus = DownloadStatus.QUEUED
            )
        )
        val episode = requireNotNull(dao.getEpisodeByFeedAndGuid(FEED_URL, "episode"))
        val publicationFile = File(context.cacheDir, "cancel-publication.mp3").apply { writeText("target") }
        dao.updateDownloadStatus(episode.episodeId, DownloadStatus.DOWNLOADING, publicationFile.absolutePath)
        val staging = downloadStagingFiles(context.filesDir, episode.episodeId)
        staging.partFile.apply { parentFile?.mkdirs(); writeText("partial") }
        staging.metadataFile.writeText("metadata")
        val publication = FilePublication(publicationFile)
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(Constants.DOWNLOAD_WORKER_EPISODE_ID to episode.episodeId))
                .setInitialDelay(1, TimeUnit.DAYS)
                .addTag(TEST_TAG)
                .addTag(downloadWorkName(episode.episodeId))
                .build()
        workManager.enqueueUniqueWork(
            downloadWorkName(episode.episodeId),
            ExistingWorkPolicy.REPLACE,
            request
        ).result.await()

        workManager.cancelWorkById(request.id).result.await()
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(request.id).await()?.state)
        val retained =
            handleDownloadWorkerCancellation(
                workManager,
                request.id,
                episode.episodeId,
                repository,
                repository,
                staging,
                publication
            )

        val current = requireNotNull(dao.getEpisodeById(episode.episodeId))
        assertFalse(retained)
        assertEquals(DownloadStatus.NOT_DOWNLOADED, current.downloadStatus)
        assertNull(current.downloadPath)
        assertFalse(staging.partFile.exists())
        assertFalse(staging.metadataFile.exists())
        assertFalse(publicationFile.exists())
    }

    private class FilePublication(private val file: File) : PendingDownloadPublication {
        override val path: String = file.absolutePath
        override val totalBytes: Long = file.length()

        override fun makeVisible() = Unit

        override fun cleanup() {
            file.delete()
        }
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val TEST_TAG = "sprint7_download_cancellation_test"
    }
}
