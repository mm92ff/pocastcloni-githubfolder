package com.example.pocastcloni.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.repository.toDomain
import com.example.pocastcloni.data.repository.toEpisodeEntity
import com.example.pocastcloni.playback.infrastructure.MediaStateMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class EpisodeIdentityAndroidTest {
    @Test
    fun duplicateGuidsRemainIsolatedAcrossFeeds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.podcastDao()
            dao.insertPodcast(podcast(FEED_A))
            dao.insertPodcast(podcast(FEED_B))
            dao.insertEpisodes(listOf(episode(FEED_A, "A"), episode(FEED_B, "B")))

            val episodeA = dao.getEpisodeByFeedAndGuid(FEED_A, SHARED_GUID)
            val episodeB = dao.getEpisodeByFeedAndGuid(FEED_B, SHARED_GUID)
            assertNotNull(episodeA)
            assertNotNull(episodeB)
            assertNotEquals(episodeA?.episodeId, episodeB?.episodeId)

            dao.setFavoriteStatus(episodeA!!.episodeId, true, 10L, 10L)
            dao.markEpisodePlayed(episodeB!!.episodeId, true, Date(20L))
            dao.updateEpisodeProgressOnly(episodeA.episodeId, 30L)
            dao.updateDownloadStatus(episodeB.episodeId, DownloadStatus.DOWNLOADED, "/b.mp3")
            dao.upsertEpisodesEfficient(listOf(episode(FEED_A, "A updated")))

            val updatedA = dao.getEpisodeById(episodeA.episodeId)!!
            val updatedB = dao.getEpisodeById(episodeB.episodeId)!!
            assertEquals("A updated", updatedA.title)
            assertEquals("B", updatedB.title)
            assertEquals(true, updatedA.isFavorite)
            assertEquals(false, updatedB.isFavorite)
            assertEquals(false, updatedA.isPlayed)
            assertEquals(true, updatedB.isPlayed)
            assertEquals(30L, updatedA.playbackPositionMs)
            assertEquals(0L, updatedB.playbackPositionMs)
            assertEquals(DownloadStatus.NOT_DOWNLOADED, updatedA.downloadStatus)
            assertEquals(DownloadStatus.DOWNLOADED, updatedB.downloadStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun repeatedLegacyTitleFallbackUpsertIsStableAndFeedScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.podcastDao()
            dao.insertPodcast(podcast(FEED_A))
            dao.insertPodcast(podcast(FEED_B))
            val item = RssItem(
                title = "No GUID",
                pubDate = "Mon, 01 Jan 2024 10:00:00 +0000",
                itunesDuration = "60"
            )
            val first = item.toEpisodeEntity(FEED_A)
            val repeated = item.toEpisodeEntity(FEED_A)
            val otherFeed = item.toEpisodeEntity(FEED_B)

            dao.upsertEpisodesEfficient(listOf(first))
            dao.upsertEpisodesEfficient(listOf(repeated))
            dao.upsertEpisodesEfficient(listOf(otherFeed))

            assertEquals(1, dao.getEpisodesForPodcastSync(FEED_A).size)
            assertEquals(1, dao.getEpisodesForPodcastSync(FEED_B).size)
            assertEquals(first.guid, repeated.guid)
            assertEquals(first.guid, otherFeed.guid)
        } finally {
            database.close()
        }
    }

    @Test
    fun mediaItemsUseInternalIdsWhenGuidsCollide() {
        val mapper = MediaStateMapper()
        val first = episode(FEED_A, "A").copy(episodeId = 101L)
        val second = episode(FEED_B, "B").copy(episodeId = 202L)

        val firstMediaId = mapper.mapToMediaItem(first.toDomain(), null, first.enclosureUrl).mediaId
        val secondMediaId = mapper.mapToMediaItem(second.toDomain(), null, second.enclosureUrl).mediaId

        assertEquals("101", firstMediaId)
        assertEquals("202", secondMediaId)
        assertNotEquals(firstMediaId, secondMediaId)
    }

    private fun podcast(feedUrl: String) = PodcastEntity(
        rssUrl = feedUrl,
        title = feedUrl,
        description = "",
        imageUrl = "https://example.com/cover.png"
    )

    private fun episode(
        feedUrl: String,
        title: String
    ) = EpisodeEntity(
        guid = SHARED_GUID,
        podcastRssUrl = feedUrl,
        title = title,
        description = "Description",
        pubDate = Date(1_700_000_000_000L),
        link = "https://example.com/episode",
        enclosureUrl = "https://example.com/episode.mp3"
    )

    private companion object {
        const val FEED_A = "https://example.com/a.xml"
        const val FEED_B = "https://example.com/b.xml"
        const val SHARED_GUID = "shared-guid"
    }
}
