package com.example.pocastcloni.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadStatusTransitionAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PodcastDao
    private var episodeId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.podcastDao()
        dao.insertPodcast(
            PodcastEntity(
                rssUrl = FEED_URL,
                title = "Podcast",
                description = "Description",
                imageUrl = "https://example.com/image.jpg"
            )
        )
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
        episodeId = requireNotNull(dao.getEpisodeByFeedAndGuid(FEED_URL, "episode")).episodeId
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cancelledStateCannotBeOverwrittenByStaleWorker() = runBlocking {
        assertEquals(
            1,
            dao.compareAndSetDownloadStatus(
                episodeId,
                listOf(DownloadStatus.QUEUED),
                DownloadStatus.DOWNLOADING,
                null
            )
        )
        assertEquals(
            1,
            dao.compareAndSetDownloadStatus(
                episodeId,
                listOf(DownloadStatus.DOWNLOADING),
                DownloadStatus.NOT_DOWNLOADED,
                null
            )
        )

        assertEquals(
            0,
            dao.compareAndSetDownloadStatus(
                episodeId,
                listOf(DownloadStatus.DOWNLOADING),
                DownloadStatus.DOWNLOADED,
                "/stale.mp3"
            )
        )
        assertEquals(DownloadStatus.NOT_DOWNLOADED, dao.getEpisodeById(episodeId)?.downloadStatus)
    }

    @Test
    fun completedPathCannotBeReplacedByStaleWorker() = runBlocking {
        dao.compareAndSetDownloadStatus(
            episodeId,
            listOf(DownloadStatus.QUEUED),
            DownloadStatus.DOWNLOADING,
            null
        )
        dao.compareAndSetDownloadStatus(
            episodeId,
            listOf(DownloadStatus.DOWNLOADING),
            DownloadStatus.DOWNLOADED,
            "/committed.mp3"
        )

        assertEquals(
            0,
            dao.compareAndSetDownloadStatus(
                episodeId,
                listOf(DownloadStatus.DOWNLOADING),
                DownloadStatus.FAILED,
                null
            )
        )
        assertEquals("/committed.mp3", dao.getEpisodeById(episodeId)?.downloadPath)
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
    }
}
