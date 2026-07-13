package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.BackupRoomSnapshot
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.EpisodeFeedUpdate
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.data.local.PodcastEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun portableStateRoundTripKeepsDuplicateGuidsSeparateAndLocalDownloadsUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.podcastDao()
            val transactionRunner = BackupImportTransactionRunner(database)
            val feedA = "https://feed-a.example/rss"
            val feedB = "https://feed-b.example/rss"
            val sharedGuid = "shared-guid"
            dao.insertPodcasts(
                listOf(
                    PodcastEntity(
                        feedB,
                        "Feed B",
                        "",
                        "",
                        autoDownloadEnabled = false,
                        sortOrder = 0
                    ),
                    PodcastEntity(
                        feedA,
                        "Feed A",
                        "",
                        "",
                        autoDownloadEnabled = true,
                        sortOrder = 1
                    )
                )
            )
            dao.insertEpisodes(
                listOf(
                    portableEpisode(
                        feedUrl = feedA,
                        guid = sharedGuid,
                        title = "Episode A",
                        favoriteTimestamp = 20,
                        favoriteAddedAt = 100,
                        isPlayed = true,
                        datePlayed = Date(300),
                        playbackPositionMs = 50_000,
                        duration = 60_000,
                        downloadPath = "/local/a.mp3"
                    ),
                    portableEpisode(
                        feedUrl = feedB,
                        guid = sharedGuid,
                        title = "Episode B",
                        favoriteTimestamp = 10,
                        favoriteAddedAt = 200,
                        isPlayed = false,
                        datePlayed = null,
                        playbackPositionMs = 40_000,
                        duration = 90_000,
                        downloadPath = "/local/b.mp3"
                    )
                )
            )

            val snapshot = transactionRunner.captureBackupSnapshot()
            val backupData = BackupData(
                podcasts = snapshot.podcasts.map { it.toBackupPodcast() },
                episodeStates = snapshot.episodeStates.toBackupEpisodeStates()
            )
            assertEquals(listOf(0L, 1L), backupData.episodeStates.map { it.favoriteOrder })

            val episodeAId = requireNotNull(dao.getEpisodeByFeedAndGuid(feedA, sharedGuid)).episodeId
            val episodeBId = requireNotNull(dao.getEpisodeByFeedAndGuid(feedB, sharedGuid)).episodeId
            dao.updatePortableEpisodeState(episodeAId, false, null, false, null, 0, 1, true)
            dao.updatePortableEpisodeState(episodeBId, false, null, false, null, 0, 1, true)
            dao.updateAutoDownloadEnabled(feedA, false)
            dao.updateAutoDownloadEnabled(feedB, true)
            dao.updatePodcastSortOrders(
                listOf(PodcastSortUpdate(feedA, 9), PodcastSortUpdate(feedB, 9))
            )

            val importedPodcasts = backupData.podcasts.normalizedForImport()
            importedPodcasts.forEach { podcast ->
                dao.updateAutoDownloadEnabled(podcast.url, podcast.autoDownloadEnabled)
            }
            dao.updatePodcastSortOrders(
                mergedPodcastSortUpdates(importedPodcasts, dao.getAllPodcastsForExport())
            )
            val restore = restoreAvailableEpisodeStates(dao, backupData, restoreDuration = true)

            assertEquals(EpisodeRestoreResult(requestedFavorites = 2, restoredFavorites = 2), restore)
            val restoredA = requireNotNull(dao.getEpisodeByFeedAndGuid(feedA, sharedGuid))
            val restoredB = requireNotNull(dao.getEpisodeByFeedAndGuid(feedB, sharedGuid))
            assertTrue(restoredA.isFavorite)
            assertTrue(restoredA.isPlayed)
            assertEquals(Date(300), restoredA.datePlayed)
            assertEquals(50_000L, restoredA.playbackPositionMs)
            assertEquals(60_000L, restoredA.duration)
            assertEquals("/local/a.mp3", restoredA.downloadPath)
            assertEquals(DownloadStatus.DOWNLOADED, restoredA.downloadStatus)
            assertTrue(restoredB.isFavorite)
            assertFalse(restoredB.isPlayed)
            assertEquals(40_000L, restoredB.playbackPositionMs)
            assertEquals(90_000L, restoredB.duration)
            assertEquals("/local/b.mp3", restoredB.downloadPath)
            assertEquals(DownloadStatus.DOWNLOADED, restoredB.downloadStatus)

            val restoredPodcasts = dao.getAllPodcastsForExport()
            assertEquals(listOf(feedB, feedA), restoredPodcasts.map { it.rssUrl })
            assertEquals(listOf(0L, 1L), restoredPodcasts.map { it.sortOrder })
            assertEquals(listOf(false, true), restoredPodcasts.map { it.autoDownloadEnabled })
            val restoredFavorites = dao.getFavoriteEpisodesSync()
            assertEquals(listOf(feedA, feedB), restoredFavorites.map { it.podcastRssUrl })
            assertEquals(listOf(1L, 0L), restoredFavorites.map { it.favoriteTimestamp })
        } finally {
            database.close()
        }
    }

    @Test
    fun backupReadTransactionCannotMixPodcastBeforeDeleteWithEpisodesAfterDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.podcastDao()
            val transactionRunner = BackupImportTransactionRunner(database)
            val feedUrl = "https://example.com/atomic.xml"
            dao.insertPodcast(PodcastEntity(feedUrl, "Atomic", "", ""))
            dao.insertEpisode(
                portableEpisode(
                    feedUrl = feedUrl,
                    guid = "atomic-guid",
                    title = "Atomic episode",
                    favoriteTimestamp = 1,
                    favoriteAddedAt = 1,
                    isPlayed = false,
                    datePlayed = null,
                    playbackPositionMs = 1,
                    duration = 2,
                    downloadPath = null
                )
            )
            val podcastRead = CompletableDeferred<Unit>()
            val continueSnapshot = CompletableDeferred<Unit>()
            val snapshot = async(Dispatchers.IO) {
                transactionRunner.runForResult {
                    val podcasts = dao.getAllPodcastsForExport()
                    podcastRead.complete(Unit)
                    continueSnapshot.await()
                    BackupRoomSnapshot(podcasts, dao.getPortableEpisodeStatesForExport())
                }
            }
            podcastRead.await()
            val delete = async(Dispatchers.IO) { dao.deletePodcastAtomic(feedUrl) }
            delay(100)

            assertFalse(delete.isCompleted)
            continueSnapshot.complete(Unit)
            val exported = snapshot.await()
            delete.await()

            assertEquals(listOf(feedUrl), exported.podcasts.map { it.rssUrl })
            assertEquals(listOf(feedUrl), exported.episodeStates.map { it.podcastRssUrl })
            assertTrue(dao.getAllPodcastsForExport().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun postSyncRestoreKeepsFreshDurationWhileRestoringPortableUserState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.podcastDao()
            val feedUrl = "https://example.com/fresh-duration.xml"
            val guid = "duration-guid"
            dao.insertPodcast(PodcastEntity(feedUrl, "Duration", "", ""))
            dao.insertEpisode(
                portableEpisode(
                    feedUrl = feedUrl,
                    guid = guid,
                    title = "Backup episode",
                    favoriteTimestamp = 1,
                    favoriteAddedAt = 10,
                    isPlayed = false,
                    datePlayed = null,
                    playbackPositionMs = 0,
                    duration = 100,
                    downloadPath = "/local/duration.mp3"
                )
            )
            val episodeId = requireNotNull(dao.getEpisodeByFeedAndGuid(feedUrl, guid)).episodeId
            val backupData = BackupData(
                podcasts = listOf(BackupPodcast(url = feedUrl)),
                episodeStates = listOf(
                    BackupEpisodeState(
                        podcastUrl = feedUrl,
                        episodeGuid = guid,
                        title = "Backup episode",
                        duration = 200,
                        isFavorite = true,
                        favoriteAddedAt = 10,
                        favoriteOrder = 0,
                        isPlayed = true,
                        datePlayed = 20,
                        playbackPositionMs = 30
                    )
                )
            )

            restoreAvailableEpisodeStates(dao, backupData, restoreDuration = true)
            dao.updateEpisodeMetadataByFeedKey(
                EpisodeFeedUpdate(
                    podcastRssUrl = feedUrl,
                    guid = guid,
                    title = "Fresh feed episode",
                    description = "Fresh description",
                    pubDate = Date(300),
                    duration = 400,
                    link = "$feedUrl/fresh",
                    enclosureUrl = "$feedUrl/fresh.mp3",
                    fileSize = 500,
                    type = "audio/mpeg"
                )
            )
            dao.setFavoriteStatus(episodeId, false, null, null)
            dao.markEpisodePlayed(episodeId, false, null)
            dao.updateEpisodeProgressOnly(episodeId, 0)

            restoreAvailableEpisodeStates(dao, backupData, restoreDuration = false)

            val restored = requireNotNull(dao.getEpisodeById(episodeId))
            assertEquals("Fresh feed episode", restored.title)
            assertEquals(400L, restored.duration)
            assertTrue(restored.isFavorite)
            assertEquals(10L, restored.favoriteAddedAt)
            assertTrue(restored.isPlayed)
            assertEquals(Date(20), restored.datePlayed)
            assertEquals(30L, restored.playbackPositionMs)
            assertEquals(DownloadStatus.DOWNLOADED, restored.downloadStatus)
            assertEquals("/local/duration.mp3", restored.downloadPath)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedLocalRestoreTransactionRollsBackPortableStateAndPodcastSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.podcastDao()
            val transactionRunner = BackupImportTransactionRunner(database)
            val feedUrl = "https://example.com/rollback.xml"
            dao.insertPodcast(
                PodcastEntity(
                    feedUrl,
                    "Rollback",
                    "",
                    "",
                    autoDownloadEnabled = false,
                    sortOrder = 4
                )
            )
            dao.insertEpisode(
                portableEpisode(
                    feedUrl = feedUrl,
                    guid = "rollback-guid",
                    title = "Rollback episode",
                    favoriteTimestamp = 5,
                    favoriteAddedAt = 6,
                    isPlayed = false,
                    datePlayed = null,
                    playbackPositionMs = 7,
                    duration = 8,
                    downloadPath = "/local/rollback.mp3"
                )
            )
            val episodeId = requireNotNull(
                dao.getEpisodeByFeedAndGuid(feedUrl, "rollback-guid")
            ).episodeId

            try {
                transactionRunner.run {
                    dao.updateAutoDownloadEnabled(feedUrl, true)
                    dao.updatePodcastSortOrders(listOf(PodcastSortUpdate(feedUrl, 0)))
                    dao.updatePortableEpisodeState(
                        episodeId,
                        false,
                        null,
                        true,
                        Date(10),
                        11,
                        12,
                        true
                    )
                    error("Injected restore failure")
                }
            } catch (_: IllegalStateException) {
                // Expected.
            }

            val podcast = requireNotNull(dao.getPodcastByUrl(feedUrl))
            val episode = requireNotNull(dao.getEpisodeById(episodeId))
            assertFalse(podcast.autoDownloadEnabled)
            assertEquals(4L, podcast.sortOrder)
            assertTrue(episode.isFavorite)
            assertFalse(episode.isPlayed)
            assertEquals(7L, episode.playbackPositionMs)
            assertEquals(8L, episode.duration)
            assertEquals(DownloadStatus.DOWNLOADED, episode.downloadStatus)
            assertEquals("/local/rollback.mp3", episode.downloadPath)
        } finally {
            database.close()
        }
    }

    private fun portableEpisode(
        feedUrl: String,
        guid: String,
        title: String,
        favoriteTimestamp: Long,
        favoriteAddedAt: Long,
        isPlayed: Boolean,
        datePlayed: Date?,
        playbackPositionMs: Long,
        duration: Long,
        downloadPath: String?
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = feedUrl,
        title = title,
        description = "$title description",
        pubDate = Date(50),
        link = "$feedUrl/episode",
        enclosureUrl = "$feedUrl/audio.mp3",
        isFavorite = true,
        favoriteTimestamp = favoriteTimestamp,
        favoriteAddedAt = favoriteAddedAt,
        isPlayed = isPlayed,
        datePlayed = datePlayed,
        playbackPositionMs = playbackPositionMs,
        duration = duration,
        downloadStatus = DownloadStatus.DOWNLOADED,
        downloadPath = downloadPath
    )
}
