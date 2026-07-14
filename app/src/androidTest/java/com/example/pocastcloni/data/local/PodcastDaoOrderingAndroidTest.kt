package com.example.pocastcloni.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class PodcastDaoOrderingAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.podcastDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun episodeListsUseEpisodeIdAsStableDateTieBreaker() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisodes(
            listOf(
                episode("dated-first", Date(1_000L)),
                episode("dated-second", Date(1_000L), isPlayed = true),
                episode("undated-first", null),
                episode("undated-second", null)
            )
        )
        val expectedGuids = listOf("dated-second", "dated-first", "undated-second", "undated-first")

        assertEquals(expectedGuids, dao.getEpisodesForPodcastSync(FEED_URL).map(EpisodeEntity::guid))
        assertEquals(expectedGuids, dao.getEpisodesFlow(FEED_URL).first().map(EpisodeEntity::guid))
        assertEquals("dated-second", dao.getLatestEpisodeGuid(FEED_URL))
        assertEquals(true, dao.isLatestEpisodePlayed(FEED_URL))

        val pagingResult = dao.getEpisodesPagingSource(FEED_URL).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = expectedGuids.size,
                placeholdersEnabled = false
            )
        )

        @Suppress("UNCHECKED_CAST")
        val page = pagingResult as PagingSource.LoadResult.Page<Int, EpisodeEntity>
        assertEquals(expectedGuids, page.data.map(EpisodeEntity::guid))
    }

    @Test
    fun historyUsesEpisodeIdAsStablePlayedDateTieBreaker() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisodes(
            listOf(
                episode("played-first", Date(1_000L), isPlayed = true, datePlayed = Date(2_000L)),
                episode("played-second", Date(1_000L), isPlayed = true, datePlayed = Date(2_000L))
            )
        )

        assertEquals(
            listOf("played-second", "played-first"),
            dao.getPlaybackHistory().first().map(EpisodeEntity::guid)
        )
        assertEquals(
            listOf("played-second", "played-first"),
            dao.getPlaybackHistoryWithPodcastLiteFlow().first().map { it.episode.guid }
        )
    }

    @Test
    fun clearHistoryPreservesPlaybackPosition() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(
                guid = "played",
                pubDate = Date(1_000L),
                isPlayed = true,
                datePlayed = Date(2_000L),
                playbackPositionMs = 750L
            )
        )
        val episodeId = dao.getEpisodeByFeedAndGuid(FEED_URL, "played")!!.episodeId

        assertEquals(1, dao.clearHistory())

        val stored = dao.getEpisodeById(episodeId)!!
        assertFalse(stored.isPlayed)
        assertNull(stored.datePlayed)
        assertEquals(750L, stored.playbackPositionMs)
    }

    private fun podcast() = PodcastEntity(
        rssUrl = FEED_URL,
        title = "Podcast",
        description = "Description",
        imageUrl = "https://example.com/cover.jpg"
    )

    private fun episode(
        guid: String,
        pubDate: Date?,
        isPlayed: Boolean = false,
        datePlayed: Date? = null,
        playbackPositionMs: Long = 0L
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = FEED_URL,
        title = guid,
        description = "Description",
        pubDate = pubDate,
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        isPlayed = isPlayed,
        datePlayed = datePlayed,
        playbackPositionMs = playbackPositionMs
    )

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
    }
}
