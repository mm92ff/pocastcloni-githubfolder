package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class BackupImportPreservesDataAndroidTest {
    @Test
    fun existingPodcastStubImportPreservesEpisodeState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.podcastDao()
            val url = "https://example.com/feed.xml"
            dao.insertPodcast(PodcastEntity(url, "Existing", "Local description", "https://example.com/cover.jpg"))
            dao.insertEpisode(
                EpisodeEntity(
                    guid = "episode-1",
                    podcastRssUrl = url,
                    title = "Existing episode",
                    description = "Keep me",
                    pubDate = Date(1_700_000_000_000),
                    link = "https://example.com/episode-1",
                    enclosureUrl = "https://example.com/episode-1.mp3",
                    isPlayed = true,
                    playbackPositionMs = 12_345,
                    downloadStatus = DownloadStatus.DOWNLOADED,
                    downloadPath = "/existing/file.mp3",
                    isFavorite = true,
                    datePlayed = Date(1_700_000_100_000),
                    favoriteTimestamp = 1_700_000_200_000,
                    favoriteAddedAt = 1_700_000_200_000
                )
            )

            insertPodcastStubPreservingExisting(
                dao,
                PodcastEntity(url, "Backup stub", "", "")
            )

            val podcast = dao.getPodcastByUrl(url)
            val episode = dao.getEpisodeByFeedAndGuid(url, "episode-1")
            assertEquals("Existing", podcast?.title)
            assertNotNull(episode)
            assertEquals(true, episode?.isFavorite)
            assertEquals(true, episode?.isPlayed)
            assertEquals(12_345L, episode?.playbackPositionMs)
            assertEquals(DownloadStatus.DOWNLOADED, episode?.downloadStatus)
            assertEquals("/existing/file.mp3", episode?.downloadPath)
        } finally {
            database.close()
        }
    }
}
