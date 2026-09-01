package com.example.pocastcloni.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class PodcastBadgeConsistencyAndroidTest {
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
    fun alreadyPlayedLatestClearsStaleBadgeWithoutChangingPlayedDate() = runBlocking {
        dao.insertPodcast(podcast(FEED_A, hasNewEpisodes = true))
        dao.insertEpisodes(
            listOf(
                episode(FEED_A, "older", Date(1_000L)),
                episode(
                    rssUrl = FEED_A,
                    guid = "latest",
                    pubDate = Date(2_000L),
                    isPlayed = true,
                    datePlayed = Date(2_500L)
                )
            )
        )
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_A, "latest")!!.episodeId

        assertEquals(
            0,
            dao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(9_000L))
        )

        val storedEpisode = dao.getEpisodeById(latestId)!!
        assertTrue(storedEpisode.isPlayed)
        assertEquals(Date(2_500L), storedEpisode.datePlayed)
        val storedPodcast = dao.getPodcastByUrl(FEED_A)!!
        assertFalse(storedPodcast.hasNewEpisodes)
        assertEquals(true, storedPodcast.isLatestEpisodePlayed)
    }

    @Test
    fun playingOlderEpisodeDoesNotClearNewerBadge() = runBlocking {
        dao.insertPodcast(podcast(FEED_A, hasNewEpisodes = true))
        dao.insertEpisodes(
            listOf(
                episode(FEED_A, "older", Date(1_000L)),
                episode(FEED_A, "latest", Date(2_000L))
            )
        )
        val olderId = dao.getEpisodeByFeedAndGuid(FEED_A, "older")!!.episodeId

        assertEquals(1, dao.markEpisodePlayedAndReconcileBadge(olderId, true, Date(3_000L)))

        assertTrue(dao.getEpisodeById(olderId)!!.isPlayed)
        assertTrue(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
    }

    @Test
    fun repeatedPlayedCommandForOlderEpisodePreservesDateAndNewerBadge() = runBlocking {
        dao.insertPodcast(podcast(FEED_A, hasNewEpisodes = true))
        dao.insertEpisodes(
            listOf(
                episode(
                    rssUrl = FEED_A,
                    guid = "older",
                    pubDate = Date(1_000L),
                    isPlayed = true,
                    datePlayed = Date(1_500L)
                ),
                episode(FEED_A, "latest", Date(2_000L))
            )
        )
        val olderId = dao.getEpisodeByFeedAndGuid(FEED_A, "older")!!.episodeId

        assertEquals(
            0,
            dao.markEpisodePlayedAndReconcileBadge(olderId, true, Date(9_000L))
        )

        assertEquals(Date(1_500L), dao.getEpisodeById(olderId)!!.datePlayed)
        assertTrue(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
    }

    @Test
    fun changingPlayedLatestBackToUnplayedReactivatesBadge() = runBlocking {
        dao.insertPodcast(podcast(FEED_A, hasNewEpisodes = false))
        dao.insertEpisode(
            episode(
                rssUrl = FEED_A,
                guid = "latest",
                pubDate = Date(2_000L),
                isPlayed = true,
                datePlayed = Date(2_500L)
            )
        )
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_A, "latest")!!.episodeId

        assertEquals(1, dao.markEpisodePlayedAndReconcileBadge(latestId, false, null))

        val storedEpisode = dao.getEpisodeById(latestId)!!
        assertFalse(storedEpisode.isPlayed)
        assertNull(storedEpisode.datePlayed)
        val storedPodcast = dao.getPodcastByUrl(FEED_A)!!
        assertTrue(storedPodcast.hasNewEpisodes)
        assertEquals(false, storedPodcast.isLatestEpisodePlayed)
    }

    @Test
    fun toggleRereadsStoredStateAndReconcilesLatestBadgeInEachTransaction() = runBlocking {
        dao.insertPodcast(podcast(FEED_A, hasNewEpisodes = true))
        dao.insertEpisode(episode(FEED_A, "latest", Date(2_000L)))
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_A, "latest")!!.episodeId

        assertEquals(
            1,
            dao.toggleEpisodePlayedAndReconcileBadge(latestId, Date(2_500L))
        )

        val playedEpisode = dao.getEpisodeById(latestId)!!
        assertTrue(playedEpisode.isPlayed)
        assertEquals(Date(2_500L), playedEpisode.datePlayed)
        assertFalse(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)

        assertEquals(
            1,
            dao.toggleEpisodePlayedAndReconcileBadge(latestId, Date(9_000L))
        )

        val unplayedEpisode = dao.getEpisodeById(latestId)!!
        assertFalse(unplayedEpisode.isPlayed)
        assertNull(unplayedEpisode.datePlayed)
        assertTrue(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
    }

    @Test
    fun startupReconciliationOnlyClearsBadgesWithPlayedLatestEpisodes() = runBlocking {
        dao.insertPodcasts(
            listOf(
                podcast(FEED_A, hasNewEpisodes = true),
                podcast(FEED_B, hasNewEpisodes = true),
                podcast(FEED_C, hasNewEpisodes = false),
                podcast(FEED_D, hasNewEpisodes = true)
            )
        )
        dao.insertEpisodes(
            listOf(
                episode(FEED_A, "played-latest", Date(2_000L), isPlayed = true),
                episode(FEED_B, "unplayed-latest", Date(2_000L)),
                episode(FEED_C, "seen-unplayed", Date(2_000L))
            )
        )

        assertEquals(1, dao.reconcilePlayedLatestEpisodeBadges())
        assertFalse(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
        assertTrue(dao.getPodcastByUrl(FEED_B)!!.hasNewEpisodes)
        assertFalse(dao.getPodcastByUrl(FEED_C)!!.hasNewEpisodes)
        assertTrue(dao.getPodcastByUrl(FEED_D)!!.hasNewEpisodes)
        assertEquals(0, dao.reconcilePlayedLatestEpisodeBadges())
    }

    @Test
    fun missingEpisodePlayedCommandIsSafeNoOp() = runBlocking {
        assertEquals(0, dao.markEpisodePlayedAndReconcileBadge(404L, true, Date(1_000L)))
        assertEquals(0, dao.toggleEpisodePlayedAndReconcileBadge(404L, Date(1_000L)))
    }

    @Test
    fun blankPodcastIdentityDoesNotAffectAnotherPodcastBadge() = runBlocking {
        dao.insertPodcasts(
            listOf(
                podcast("", hasNewEpisodes = true),
                podcast(FEED_A, hasNewEpisodes = true)
            )
        )
        dao.insertEpisode(episode("", "blank-feed-episode", Date(2_000L)))
        val episodeId = dao.getEpisodeByFeedAndGuid("", "blank-feed-episode")!!.episodeId

        assertEquals(
            1,
            dao.markEpisodePlayedAndReconcileBadge(episodeId, true, Date(3_000L))
        )

        assertTrue(dao.getEpisodeById(episodeId)!!.isPlayed)
        assertTrue(dao.getPodcastByUrl("")!!.hasNewEpisodes)
        assertTrue(dao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
    }

    @Test
    fun repairedBadgeRemainsClearedAfterDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "podcast-badge-reopen-test"
        context.deleteDatabase(databaseName)
        var fileDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

        try {
            var fileDao = fileDatabase.podcastDao()
            fileDao.insertPodcast(podcast(FEED_A, hasNewEpisodes = true))
            fileDao.insertEpisode(
                episode(FEED_A, "played-latest", Date(2_000L), isPlayed = true)
            )
            assertEquals(1, fileDao.reconcilePlayedLatestEpisodeBadges())
            fileDatabase.close()

            fileDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            fileDao = fileDatabase.podcastDao()

            assertFalse(fileDao.getPodcastByUrl(FEED_A)!!.hasNewEpisodes)
            assertTrue(fileDao.getEpisodeByFeedAndGuid(FEED_A, "played-latest")!!.isPlayed)
        } finally {
            if (fileDatabase.isOpen) fileDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun podcast(
        rssUrl: String,
        hasNewEpisodes: Boolean
    ) = PodcastEntity(
        rssUrl = rssUrl,
        title = rssUrl,
        description = "Description",
        imageUrl = "https://example.com/cover.jpg",
        hasNewEpisodes = hasNewEpisodes
    )

    private fun episode(
        rssUrl: String,
        guid: String,
        pubDate: Date?,
        isPlayed: Boolean = false,
        datePlayed: Date? = null
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = rssUrl,
        title = guid,
        description = "Description",
        pubDate = pubDate,
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        isPlayed = isPlayed,
        datePlayed = datePlayed
    )

    private companion object {
        const val FEED_A = "https://example.com/a.xml"
        const val FEED_B = "https://example.com/b.xml"
        const val FEED_C = "https://example.com/c.xml"
        const val FEED_D = "https://example.com/d.xml"
    }
}
