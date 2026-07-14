package com.example.pocastcloni.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.ItunesResponse
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.data.worker.downloadStagingFiles
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

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
            context = context
        )
    }

    private val downloadsDir: File
        get() = File(context.filesDir, Constants.DOWNLOADS_DIR)
    private val insertedMediaStoreRows = mutableListOf<Uri>()

    @Before
    fun setUp() =
        runBlocking {
            downloadsDir.deleteRecursively()
            dao.deleteAllPodcasts()
        }

    @After
    fun tearDown() =
        runBlocking {
            insertedMediaStoreRows.forEach { context.contentResolver.delete(it, null, null) }
            insertedMediaStoreRows.clear()
            downloadsDir.deleteRecursively()
            dao.deleteAllPodcasts()
        }

    @Test
    fun reconcileEpisodeStorage_resetsBrokenAndTransientDownloadStates() =
        runBlocking {
            dao.insertPodcast(
                PodcastEntity(
                    rssUrl = FEED_URL,
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
                    ),
                    episode(
                        guid = "active-queued",
                        downloadStatus = DownloadStatus.QUEUED,
                        pubDateMs = 500
                    )
                )
            )

            val orphanQueued = requireNotNull(dao.getEpisodeByFeedAndGuid(FEED_URL, "queued"))
            val activeQueued = requireNotNull(dao.getEpisodeByFeedAndGuid(FEED_URL, "active-queued"))
            val orphanStaging = downloadStagingFiles(context.filesDir, orphanQueued.episodeId)
            val activeStaging = downloadStagingFiles(context.filesDir, activeQueued.episodeId)
            orphanStaging.partFile.apply { parentFile?.mkdirs(); writeText("partial") }
            orphanStaging.metadataFile.writeText("metadata")
            activeStaging.partFile.apply { parentFile?.mkdirs(); writeText("partial") }
            activeStaging.metadataFile.writeText("metadata")

            val correctedEntries = repository.reconcileEpisodeStorage(setOf(activeQueued.episodeId))

            assertEquals(3, correctedEntries)
            assertEquals(
                DownloadStatus.DOWNLOADED,
                dao.getEpisodeByFeedAndGuid(FEED_URL, "kept-downloaded")?.downloadStatus
            )
            assertEquals(
                DownloadStatus.NOT_DOWNLOADED,
                dao.getEpisodeByFeedAndGuid(FEED_URL, "missing-downloaded")?.downloadStatus
            )
            assertNull(
                dao.getEpisodeByFeedAndGuid(FEED_URL, "missing-downloaded")?.downloadPath
            )
            assertEquals(
                DownloadStatus.NOT_DOWNLOADED,
                dao.getEpisodeByFeedAndGuid(FEED_URL, "queued")?.downloadStatus
            )
            assertEquals(
                DownloadStatus.NOT_DOWNLOADED,
                dao.getEpisodeByFeedAndGuid(FEED_URL, "downloading")?.downloadStatus
            )
            assertEquals(
                DownloadStatus.QUEUED,
                dao.getEpisodeByFeedAndGuid(FEED_URL, "active-queued")?.downloadStatus
            )
            assertEquals(false, orphanStaging.partFile.exists())
            assertEquals(false, orphanStaging.metadataFile.exists())
            assertEquals(true, activeStaging.partFile.exists())
            assertEquals(true, activeStaging.metadataFile.exists())
        }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun reconcileEpisodeStorage_publishesReferencedPendingMediaStoreRowAndDeletesOrphan() =
        runBlocking {
            dao.insertPodcast(
                PodcastEntity(
                    rssUrl = FEED_URL,
                    title = "Example Podcast",
                    description = "Description",
                    imageUrl = "https://example.com/image.png"
                )
            )
            val referenced = insertPendingMediaStoreDownload("referenced.mp3")
            val orphan = insertPendingMediaStoreDownload("orphan.mp3")
            dao.insertEpisodes(
                listOf(
                    episode(
                        guid = "pending-media-store",
                        downloadStatus = DownloadStatus.DOWNLOADED,
                        downloadPath = referenced.toString(),
                        pubDateMs = 1_000
                    )
                )
            )

            repository.reconcileEpisodeStorage()

            val episode = requireNotNull(dao.getEpisodeByFeedAndGuid(FEED_URL, "pending-media-store"))
            assertEquals(DownloadStatus.DOWNLOADED, episode.downloadStatus)
            assertEquals(referenced.toString(), episode.downloadPath)
            assertEquals(0, pendingFlag(referenced))
            assertFalse(mediaStoreRowExists(orphan))
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun insertPendingMediaStoreDownload(displayName: String): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Pocastcloni/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            )
        insertedMediaStoreRows += uri
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write("audio".toByteArray()) }
        return uri
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun pendingFlag(uri: Uri): Int? =
        context.contentResolver
            .query(uri, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Downloads.IS_PENDING))
                } else {
                    null
                }
            }

    private fun mediaStoreRowExists(uri: Uri): Boolean =
        context.contentResolver.query(uri, arrayOf(MediaStore.Downloads._ID), null, null, null)?.use {
            it.moveToFirst()
        } ?: false

    private fun episode(
        guid: String,
        downloadStatus: DownloadStatus,
        downloadPath: String? = null,
        pubDateMs: Long
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = FEED_URL,
        title = guid,
        description = guid,
        pubDate = Date(pubDateMs),
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        downloadStatus = downloadStatus,
        downloadPath = downloadPath
    )

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
    }
}
