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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
                episode(
                    guid = "new-guid",
                    title = "New episode",
                    episodeId = 0L,
                    userState = false
                )
                    .copy(pubDate = Date(3_000L))
                    .toDomain()
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
        assertNull(storedPodcast.lastSeenEpisodeGuid)
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
        assertEquals(EXISTING_GUID, storedPodcast.lastSeenEpisodeGuid)

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
    fun duplicateSyncBeforeMarkAllAsSeenStaysCleared() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(episode(episodeId = 0L, userState = false))

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes = listOf(refreshedEpisode().toDomain())
        )
        dao.markAllAsSeen()

        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertFalse(storedPodcast.hasNewEpisodes)
        assertEquals(EXISTING_GUID, storedPodcast.lastSeenEpisodeGuid)
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

        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertFalse(storedPodcast.hasNewEpisodes)
        assertEquals("second", storedPodcast.lastSeenEpisodeGuid)
        val episodes = dao.getEpisodesForPodcastSync(FEED_URL)
        assertTrue(episodes.all { !it.isPlayed && it.datePlayed == null })
        assertTrue(dao.getPlaybackHistory().first().isEmpty())
    }

    @Test
    fun olderBackfillDoesNotReactivateClearedBadge() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "acknowledged-latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(3_000L))
        )
        dao.markAllAsSeen()

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "older-backfill-a", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
                    .toDomain(),
                episode(guid = "older-backfill-b", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(2_000L))
                    .toDomain()
            )
        )

        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertFalse(storedPodcast.hasNewEpisodes)
        assertEquals("acknowledged-latest", storedPodcast.lastSeenEpisodeGuid)
        assertEquals("acknowledged-latest", dao.getLatestEpisodeGuid(FEED_URL))
    }

    @Test
    fun olderBackfillPreservesActiveBadge() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "current-latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(3_000L))
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "older-backfill", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
                    .toDomain()
            )
        )

        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        assertEquals("current-latest", dao.getLatestEpisodeGuid(FEED_URL))
    }

    @Test
    fun duplicateOnlySyncPreservesActiveBadge() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(episode(episodeId = 0L, userState = false))

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes = listOf(refreshedEpisode().toDomain())
        )

        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        assertEquals(1, dao.getEpisodesForPodcastSync(FEED_URL).size)
    }

    @Test
    fun metadataReorderToDifferentUnseenLatestActivatesBadge() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisodes(
            listOf(
                episode(guid = "reordered", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L)),
                episode(guid = "previous-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(2_000L))
            )
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "reordered", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(3_000L))
                    .toDomain()
            )
        )

        assertEquals("reordered", dao.getLatestEpisodeGuid(FEED_URL))
        assertEquals(2, dao.getEpisodesForPodcastSync(FEED_URL).size)
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun genuinelyNewerInsertActivatesBadge() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(guid = "old-latest", episodeId = 0L)
                .copy(pubDate = Date(1_000L))
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "new-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(3_000L))
                    .toDomain(),
                episode(guid = "older-in-same-sync", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(500L))
                    .toDomain()
            )
        )

        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        assertEquals("new-latest", dao.getLatestEpisodeGuid(FEED_URL))
        assertEquals(false, dao.getPodcastByUrl(FEED_URL)!!.isLatestEpisodePlayed)
    }

    @Test
    fun equalDatedInsertedEpisodeUsesEpisodeIdTieBreaker() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(guid = "equal-date-existing", episodeId = 0L)
                .copy(pubDate = Date(2_000L))
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "equal-date-inserted", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(2_000L))
                    .toDomain()
            )
        )

        assertEquals("equal-date-inserted", dao.getLatestEpisodeGuid(FEED_URL))
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun nullDatedInsertedEpisodeUsesEpisodeIdTieBreaker() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(guid = "undated-existing", episodeId = 0L)
                .copy(pubDate = null)
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "undated-inserted", episodeId = 0L, userState = false)
                    .copy(pubDate = null)
                    .toDomain()
            )
        )

        assertEquals("undated-inserted", dao.getLatestEpisodeGuid(FEED_URL))
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun playingInsertedLatestClearsBadgeEndToEnd() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(guid = "previous-latest", episodeId = 0L)
                .copy(pubDate = Date(1_000L))
        )
        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "new-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(3_000L))
                    .toDomain()
            )
        )
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "new-latest")!!.episodeId

        dao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(4_000L))

        assertTrue(dao.getEpisodeById(latestId)!!.isPlayed)
        assertFalse(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        assertFalse(dao.getAllPodcastsWithCoverFlow().first().single().toDomain().hasNewEpisodes)
    }

    @Test
    fun homeQueryFlowEmitsClearedActiveClearedForNewLatestLifecycle() = runBlocking {
        withTimeout(5_000L) {
            dao.insertPodcast(podcast())
            dao.insertEpisode(
                episode(guid = "previous-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
            )
            val initialObserved = CompletableDeferred<Unit>()
            val activeObserved = CompletableDeferred<Unit>()
            val clearedObserved = CompletableDeferred<Unit>()
            val emissions = async {
                dao.getAllPodcastsWithCoverFlow()
                    .map { rows -> rows.single().toDomain().hasNewEpisodes }
                    .distinctUntilChanged()
                    .onEach { hasNewEpisodes ->
                        when {
                            hasNewEpisodes -> activeObserved.complete(Unit)
                            initialObserved.isCompleted -> clearedObserved.complete(Unit)
                            else -> initialObserved.complete(Unit)
                        }
                    }
                    .take(3)
                    .toList()
            }
            initialObserved.await()

            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes =
                listOf(
                    episode(guid = "new-latest", episodeId = 0L, userState = false)
                        .copy(pubDate = Date(3_000L))
                        .toDomain()
                )
            )
            activeObserved.await()

            val latestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "new-latest")!!.episodeId
            dao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(4_000L))
            clearedObserved.await()

            assertEquals(listOf(false, true, false), emissions.await())
        }
    }

    @Test
    fun initialSubscriptionHistoryDoesNotActivateBadge() = runBlocking {
        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = podcast().copy(hasNewEpisodes = true).toDomain(),
            episodes =
            listOf(
                episode(guid = "initial-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(3_000L))
                    .toDomain(),
                episode(guid = "initial-older", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
                    .toDomain()
            )
        )

        val storedPodcast = dao.getPodcastByUrl(FEED_URL)!!
        assertFalse(storedPodcast.hasNewEpisodes)
        assertEquals("initial-latest", storedPodcast.lastSeenEpisodeGuid)
        assertEquals(2, dao.getEpisodesForPodcastSync(FEED_URL).size)
    }

    @Test
    fun playedLatestFollowedByConcurrentOlderBackfillStaysCleared() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(3_000L))
        )
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "latest")!!.episodeId
        val transactionEntered = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val playedWrite = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                transactionEntered.complete(Unit)
                releaseTransaction.await()
                dao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(4_000L))
            }
        }
        transactionEntered.await()
        val backfillWrite = async(start = CoroutineStart.UNDISPATCHED) {
            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes =
                listOf(
                    episode(guid = "older", episodeId = 0L, userState = false)
                        .copy(pubDate = Date(1_000L))
                        .toDomain()
                )
            )
        }

        releaseTransaction.complete(Unit)
        playedWrite.await()
        backfillWrite.await()

        assertTrue(dao.getEpisodeById(latestId)!!.isPlayed)
        assertFalse(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun olderBackfillBeforePlayingLatestStaysCleared() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(3_000L))
        )

        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "older", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
                    .toDomain()
            )
        )
        val latestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "latest")!!.episodeId
        dao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(4_000L))

        assertTrue(dao.getEpisodeById(latestId)!!.isPlayed)
        assertFalse(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun startupReconciliationRacingDuplicateSyncCannotReactivateBadge() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "played-latest", episodeId = 0L, userState = true)
                .copy(pubDate = Date(3_000L))
        )
        val transactionEntered = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val reconciliation = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                transactionEntered.complete(Unit)
                releaseTransaction.await()
                dao.reconcileLatestEpisodeBadges()
            }
        }
        transactionEntered.await()
        val duplicateSync = async(start = CoroutineStart.UNDISPATCHED) {
            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes =
                listOf(
                    episode(guid = "played-latest", episodeId = 0L, userState = false)
                        .copy(pubDate = Date(3_000L))
                        .toDomain()
                )
            )
        }

        releaseTransaction.complete(Unit)
        assertEquals(1, reconciliation.await())
        duplicateSync.await()

        assertFalse(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
        assertTrue(dao.getEpisodeByFeedAndGuid(FEED_URL, "played-latest")!!.isPlayed)
    }

    @Test
    fun playedOldLatestFollowedByConcurrentNewerInsertStaysActive() = runBlocking {
        dao.insertPodcast(podcast().copy(hasNewEpisodes = true))
        dao.insertEpisode(
            episode(guid = "old-latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(2_000L))
        )
        val oldLatestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "old-latest")!!.episodeId
        val transactionEntered = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val playedWrite = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                transactionEntered.complete(Unit)
                releaseTransaction.await()
                dao.markEpisodePlayedAndReconcileBadge(oldLatestId, true, Date(3_000L))
            }
        }
        transactionEntered.await()
        val newerWrite = async(start = CoroutineStart.UNDISPATCHED) {
            persistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes =
                listOf(
                    episode(guid = "new-latest", episodeId = 0L, userState = false)
                        .copy(pubDate = Date(4_000L))
                        .toDomain()
                )
            )
        }

        releaseTransaction.complete(Unit)
        playedWrite.await()
        newerWrite.await()

        assertTrue(dao.getEpisodeById(oldLatestId)!!.isPlayed)
        assertEquals("new-latest", dao.getLatestEpisodeGuid(FEED_URL))
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun latePlayedCallbackForOlderEpisodeCannotClearNewerBadge() = runBlocking {
        dao.insertPodcast(podcast())
        dao.insertEpisode(
            episode(guid = "old-latest", episodeId = 0L, userState = false)
                .copy(pubDate = Date(2_000L))
        )
        val oldLatestId = dao.getEpisodeByFeedAndGuid(FEED_URL, "old-latest")!!.episodeId
        persistence.persistFeedUpdate(
            update = feedUpdate(),
            newPodcast = null,
            episodes =
            listOf(
                episode(guid = "new-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(4_000L))
                    .toDomain()
            )
        )

        dao.markEpisodePlayedAndReconcileBadge(oldLatestId, true, Date(5_000L))

        assertEquals("new-latest", dao.getLatestEpisodeGuid(FEED_URL))
        assertTrue(dao.getPodcastByUrl(FEED_URL)!!.hasNewEpisodes)
    }

    @Test
    fun processReopenAndStartupRepairPreserveHomeDomainInvariant() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "podcast-badge-lifecycle-test"
        context.deleteDatabase(databaseName)
        var fileDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

        try {
            var fileDao = fileDatabase.podcastDao()
            val filePersistence = RoomFeedSyncPersistence(fileDatabase, fileDao)
            fileDao.insertPodcast(podcast())
            fileDao.insertEpisode(
                episode(guid = "previous-latest", episodeId = 0L, userState = false)
                    .copy(pubDate = Date(1_000L))
            )
            filePersistence.persistFeedUpdate(
                update = feedUpdate(),
                newPodcast = null,
                episodes =
                listOf(
                    episode(guid = "new-latest", episodeId = 0L, userState = false)
                        .copy(pubDate = Date(3_000L))
                        .toDomain()
                )
            )
            assertTrue(
                fileDao.getAllPodcastsWithCoverFlow().first().single().toDomain().hasNewEpisodes
            )

            val latestId = fileDao.getEpisodeByFeedAndGuid(FEED_URL, "new-latest")!!.episodeId
            fileDao.markEpisodePlayedAndReconcileBadge(latestId, true, Date(4_000L))
            assertFalse(
                fileDao.getAllPodcastsWithCoverFlow().first().single().toDomain().hasNewEpisodes
            )

            fileDao.updatePodcastNewFlag(FEED_URL, hasNew = true)
            fileDatabase.close()
            fileDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            fileDao = fileDatabase.podcastDao()

            assertEquals(1, fileDao.reconcileLatestEpisodeBadges())
            assertTrue(fileDao.getEpisodeById(latestId)!!.isPlayed)
            assertFalse(
                fileDao.getAllPodcastsWithCoverFlow().first().single().toDomain().hasNewEpisodes
            )
        } finally {
            if (fileDatabase.isOpen) fileDatabase.close()
            context.deleteDatabase(databaseName)
        }
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
