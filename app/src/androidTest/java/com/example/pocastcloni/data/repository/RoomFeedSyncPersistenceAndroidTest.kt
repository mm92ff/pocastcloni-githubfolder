package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.FavoriteOrderUpdate
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.domain.model.FeedPodcastUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class RoomFeedSyncPersistenceAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PodcastDao
    private lateinit var persistence: RoomFeedSyncPersistence

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.podcastDao()
        persistence = RoomFeedSyncPersistence(database, dao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun successfulFeedUpdateCommitsMetadataHeadersAndEpisodesTogether() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(episode(title = "Old episode"))

        val autoDownloadEnabled = persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes = listOf(
                refreshedEpisode().toDomain(),
                episode(guid = "new-guid", title = "New episode", episodeId = 0L).toDomain()
            )
        )

        assertTrue(autoDownloadEnabled)
        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertEquals(FEED_URL, storedPodcast.rssUrl)
        assertEquals("Updated podcast", storedPodcast.title)
        assertEquals("Updated description", storedPodcast.description)
        assertEquals("https://example.com/updated.jpg", storedPodcast.imageUrl)
        assertEquals("new-last-modified", storedPodcast.lastModifiedHeader)
        assertEquals("new-etag", storedPodcast.eTagHeader)
        assertEquals(Date(2_000L), storedPodcast.lastRefreshed)
        assertEquals(9L, storedPodcast.sortOrder)
        assertTrue(storedPodcast.autoDownloadEnabled)
        assertTrue(storedPodcast.allowInsecureHttp)
        assertTrue(storedPodcast.allowLocalNetwork)
        assertTrue(storedPodcast.hasNewEpisodes)
        assertEquals("latest-guid", storedPodcast.latestEpisodeGuid)
        assertEquals(Date(900L), storedPodcast.latestEpisodePubDate)
        assertEquals(false, storedPodcast.isLatestEpisodePlayed)

        val episodes = dao.getEpisodesForPodcastSync(FEED_URL)
        assertEquals(2, episodes.size)
        val updated = episodes.single { it.guid == EXISTING_GUID }
        assertEquals("Updated episode", updated.title)
        assertEquals("Updated episode description", updated.description)
        assertEquals(Date(2_500L), updated.pubDate)
        assertEquals("https://example.com/updated-link", updated.link)
        assertEquals("https://example.com/updated.mp3", updated.enclosureUrl)
        assertEquals("audio/ogg", updated.type)
        assertEquals(4_096L, updated.fileSize)
        assertEquals(900L, updated.duration)
        assertEquals(EXISTING_GUID, updated.guid)
        assertEquals(FEED_URL, updated.podcastRssUrl)
        assertEquals(41L, updated.episodeId)
        assertTrue(updated.isFavorite)
        assertEquals(100L, updated.favoriteAddedAt)
        assertEquals(200L, updated.favoriteTimestamp)
        assertTrue(updated.isPlayed)
        assertEquals(Date(250L), updated.datePlayed)
        assertEquals(300L, updated.playbackPositionMs)
        assertEquals(DownloadStatus.DOWNLOADED, updated.downloadStatus)
        assertEquals("/existing.mp3", updated.downloadPath)
    }

    @Test
    fun episodeWriteFailureRollsBackPodcastMetadataAndHeaders() = runBlocking {
        dao.insertPodcast(podcast())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_feed_episode_insert
            BEFORE INSERT ON episodes
            BEGIN
                SELECT RAISE(ABORT, 'injected episode failure');
            END
            """.trimIndent()
        )

        val result = runCatching {
            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes = listOf(episode(episodeId = 0L).toDomain())
            )
        }

        assertTrue(result.isFailure)
        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertEquals("Original podcast", storedPodcast.title)
        assertEquals("old-last-modified", storedPodcast.lastModifiedHeader)
        assertEquals("old-etag", storedPodcast.eTagHeader)
        assertEquals(Date(1_000L), storedPodcast.lastRefreshed)
        assertTrue(dao.getEpisodesForPodcastSync(FEED_URL).isEmpty())
    }

    @Test
    fun notModifiedTouchChangesOnlyLastRefreshed() = runBlocking {
        val original = podcast()
        dao.insertPodcast(original)

        val autoDownloadEnabled = persistence.touchLastRefreshed(FEED_URL, Date(3_000L))

        assertTrue(autoDownloadEnabled)
        assertEquals(original.copy(lastRefreshed = Date(3_000L)), dao.getPodcastByUrl(FEED_URL))
    }

    @Test
    fun conflictingParentInsertDoesNotCascadeDeleteEpisodes() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(episode())

        val result = runCatching {
            dao.insertPodcast(podcast().copy(title = "Replacement"))
        }

        assertTrue(result.isFailure)
        assertEquals("Original podcast", dao.getPodcastByUrl(FEED_URL)?.title)
        assertEquals(1, dao.getEpisodesForPodcastSync(FEED_URL).size)
    }

    @Test
    fun concurrentAutoDownloadChangeIsReturnedAfterFeedCommit() = runBlocking {
        dao.insertPodcast(podcast().copy(autoDownloadEnabled = false))

        val transactionEntered = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val settingsWrite = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                transactionEntered.complete(Unit)
                releaseTransaction.await()
                dao.updateAutoDownloadEnabled(FEED_URL, true)
            }
        }
        transactionEntered.await()
        val feedWrite = async(start = CoroutineStart.UNDISPATCHED) {
            persistence.persistFeedUpdate(feedUpdate(), null, emptyList())
        }

        releaseTransaction.complete(Unit)
        settingsWrite.await()

        assertTrue(feedWrite.await())
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.autoDownloadEnabled)
    }

    @Test
    fun markSeenBeforeDuplicateSyncIsNotOverwritten() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(episode(episodeId = 0L))
        val episodeId = dao.getEpisodeByFeedAndGuid(FEED_URL, EXISTING_GUID)!!.episodeId

        val transactionEntered = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val userWrite = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                transactionEntered.complete(Unit)
                releaseTransaction.await()
                dao.markAllAsSeen()
                dao.updatePodcastSortOrders(listOf(PodcastSortUpdate(FEED_URL, 77L)))
                dao.updateFavoriteOrder(listOf(FavoriteOrderUpdate(episodeId, 500L)))
                dao.updateEpisodeProgressOnly(episodeId, 700L)
                dao.updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING, "/partial.mp3")
            }
        }
        transactionEntered.await()
        val duplicateSync = async(start = CoroutineStart.UNDISPATCHED) {
            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes = listOf(refreshedEpisode().toDomain())
            )
        }

        releaseTransaction.complete(Unit)
        userWrite.await()
        duplicateSync.await()

        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertEquals("Updated podcast", storedPodcast.title)
        assertEquals(77L, storedPodcast.sortOrder)
        assertTrue(storedPodcast.autoDownloadEnabled)
        assertTrue(storedPodcast.allowInsecureHttp)
        assertTrue(storedPodcast.allowLocalNetwork)
        assertFalse(storedPodcast.hasNewEpisodes)

        val storedEpisode = dao.getEpisodeById(episodeId)!!
        assertEquals("Updated episode", storedEpisode.title)
        assertTrue(storedEpisode.isFavorite)
        assertEquals(100L, storedEpisode.favoriteAddedAt)
        assertEquals(500L, storedEpisode.favoriteTimestamp)
        assertTrue(storedEpisode.isPlayed)
        assertEquals(700L, storedEpisode.playbackPositionMs)
        assertEquals(DownloadStatus.DOWNLOADING, storedEpisode.downloadStatus)
        assertEquals("/partial.mp3", storedEpisode.downloadPath)
    }

    @Test
    fun markAllAsSeenDoesNotChangeEpisodePlaybackStateOrHistory() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisodes(
            listOf(
                episode(guid = "first", title = "First", episodeId = 0L, userState = false).copy(pubDate = null),
                episode(guid = "second", title = "Second", episodeId = 0L, userState = false).copy(pubDate = null)
            )
        )

        dao.markAllAsSeen()

        assertFalse(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        val episodes = dao.getEpisodesForPodcastSync(FEED_URL)
        assertTrue(episodes.all { !it.isPlayed && it.datePlayed == null })
        assertTrue(dao.getPlaybackHistory().first().isEmpty())
    }

    private fun podcast() = PodcastEntity(
        rssUrl = FEED_URL,
        title = "Original podcast",
        description = "Original description",
        imageUrl = "https://example.com/original.jpg",
        lastRefreshed = Date(1_000L),
        autoDownloadEnabled = true,
        allowInsecureHttp = true,
        allowLocalNetwork = true,
        sortOrder = 9L,
        hasNewEpisodes = false,
        latestEpisodeGuid = "latest-guid",
        latestEpisodePubDate = Date(900L),
        isLatestEpisodePlayed = false,
        lastModifiedHeader = "old-last-modified",
        eTagHeader = "old-etag"
    )

    private fun feedUpdate() = FeedPodcastUpdate(
        rssUrl = FEED_URL,
        title = "Updated podcast",
        description = "Updated description",
        imageUrl = "https://example.com/updated.jpg",
        lastRefreshed = Date(2_000L),
        lastModifiedHeader = "new-last-modified",
        eTagHeader = "new-etag"
    )

    private fun refreshedEpisode() = episode(
        title = "Updated episode",
        episodeId = 0L,
        userState = false
    ).copy(
        description = "Updated episode description",
        pubDate = Date(2_500L),
        duration = 900L,
        link = "https://example.com/updated-link",
        enclosureUrl = "https://example.com/updated.mp3",
        fileSize = 4_096L,
        type = "audio/ogg"
    )

    private fun episode(
        guid: String = EXISTING_GUID,
        title: String = "Original episode",
        episodeId: Long = 41L,
        userState: Boolean = true
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = FEED_URL,
        title = title,
        description = "Description",
        pubDate = Date(1_500L),
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        isPlayed = userState,
        playbackPositionMs = if (userState) 300L else 0L,
        downloadStatus = if (userState) DownloadStatus.DOWNLOADED else DownloadStatus.NOT_DOWNLOADED,
        downloadPath = if (userState) "/existing.mp3" else null,
        isFavorite = userState,
        datePlayed = if (userState) Date(250L) else null,
        favoriteTimestamp = if (userState) 200L else null,
        favoriteAddedAt = if (userState) 100L else null,
        episodeId = episodeId
    )

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val EXISTING_GUID = "existing-guid"
    }
}
