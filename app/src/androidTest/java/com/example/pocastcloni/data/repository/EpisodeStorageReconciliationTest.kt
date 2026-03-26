package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.manager.PodcastDownloader
import com.example.pocastcloni.data.remote.ItunesResponse
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date
import javax.inject.Provider

@RunWith(AndroidJUnit4::class)
class EpisodeStorageReconciliationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.podcastDao() }

    private val repository by lazy {
        PodcastRepositoryImpl(
            podcastDao = dao,
            itunesSearchApi =
            object : ItunesSearchApi {
                override suspend fun searchPodcasts(term: String): ItunesResponse {
                    return ItunesResponse(resultCount = 0, results = emptyList())
                }
            },
            dispatcherProvider = DefaultDispatcherProvider(),
            downloader =
            PodcastDownloader(
                context,
                Provider { throw IllegalStateException("PodcastDownloader is not used in this test.") }
            ),
            syncFeedUseCase =
            Provider<SyncFeedUseCase> {
                throw IllegalStateException("SyncFeedUseCase is not used in this test.")
            }
        )
    }

    private val downloadsDir: File
        get() = File(context.filesDir, Constants.DOWNLOADS_DIR)

    @Before
    fun setUp() =
        runBlocking {
            downloadsDir.deleteRecursively()
            dao.deleteAllPodcasts()
        }

    @After
    fun tearDown() =
        runBlocking {
            downloadsDir.deleteRecursively()
            dao.deleteAllPodcasts()
        }

    @Test
    fun reconcileEpisodeStorage_resetsBrokenAndTransientDownloadStates() =
        runBlocking {
            dao.insertPodcast(
                PodcastEntity(
                    rssUrl = "https://example.com/feed.xml",
                    title = "Example Podcast",
                    description = "Description",
                    imageUrl = "https://example.com/image.png"
                )
            )

            val readableFile =
                File(downloadsDir, "kept.mp3").apply {
                    parentFile?.mkdirs()
                    writeText("audio")
                }

            dao.insertEpisodes(
                listOf(
                    episode(
                        guid = "kept-downloaded",
                        downloadStatus = DownloadStatus.DOWNLOADED,
                        downloadPath = readableFile.absolutePath,
                        pubDateMs = 4_000
                    ),
                    episode(
                        guid = "missing-downloaded",
                        downloadStatus = DownloadStatus.DOWNLOADED,
                        downloadPath = File(downloadsDir, "missing.mp3").absolutePath,
                        pubDateMs = 3_000
                    ),
                    episode(
                        guid = "queued",
                        downloadStatus = DownloadStatus.QUEUED,
                        pubDateMs = 2_000
                    ),
                    episode(
                        guid = "downloading",
                        downloadStatus = DownloadStatus.DOWNLOADING,
                        pubDateMs = 1_000
                    )
                )
            )

            val correctedEntries = repository.reconcileEpisodeStorage()

            assertEquals(3, correctedEntries)
            assertEquals(DownloadStatus.DOWNLOADED, dao.getEpisodeByGuid("kept-downloaded")?.downloadStatus)
            assertEquals(DownloadStatus.NOT_DOWNLOADED, dao.getEpisodeByGuid("missing-downloaded")?.downloadStatus)
            assertNull(dao.getEpisodeByGuid("missing-downloaded")?.downloadPath)
            assertEquals(DownloadStatus.NOT_DOWNLOADED, dao.getEpisodeByGuid("queued")?.downloadStatus)
            assertEquals(DownloadStatus.NOT_DOWNLOADED, dao.getEpisodeByGuid("downloading")?.downloadStatus)
        }

    private fun episode(
        guid: String,
        downloadStatus: DownloadStatus,
        downloadPath: String? = null,
        pubDateMs: Long
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = "https://example.com/feed.xml",
        title = guid,
        description = guid,
        pubDate = Date(pubDateMs),
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        downloadStatus = downloadStatus,
        downloadPath = downloadPath
    )
}
