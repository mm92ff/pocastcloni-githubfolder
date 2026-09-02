package com.example.pocastcloni.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastLastSeenMigrationAndroidTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )

    @Test
    fun migration17To18SurfacesUnplayedLatestAndAcknowledgesPlayedLatest() {
        helper.createDatabase(TEST_DATABASE, 17).apply {
            insertPodcast(UNPLAYED_FEED, hasNewEpisodes = false)
            insertEpisode(UNPLAYED_FEED, UNPLAYED_GUID, isPlayed = false)
            insertPodcast(PLAYED_FEED, hasNewEpisodes = true)
            insertEpisode(PLAYED_FEED, PLAYED_GUID, isPlayed = true)
            insertPodcast(EMPTY_FEED, hasNewEpisodes = true)
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DATABASE,
                18,
                true,
                AppDatabaseMigrations.MIGRATION_17_18
            )

        assertPodcastBadgeState(
            migrated,
            UNPLAYED_FEED,
            hasNewEpisodes = 1,
            lastSeenEpisodeGuid = null,
            isLatestEpisodePlayed = 0
        )
        assertPodcastBadgeState(
            migrated,
            PLAYED_FEED,
            hasNewEpisodes = 0,
            lastSeenEpisodeGuid = PLAYED_GUID,
            isLatestEpisodePlayed = 1
        )
        assertPodcastBadgeState(
            migrated,
            EMPTY_FEED,
            hasNewEpisodes = 0,
            lastSeenEpisodeGuid = null,
            isLatestEpisodePlayed = null
        )
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertPodcast(
        rssUrl: String,
        hasNewEpisodes: Boolean
    ) {
        execSQL(
            """
            INSERT INTO podcasts (
                rssUrl, title, description, imageUrl, lastRefreshed,
                autoDownloadEnabled, allowInsecureHttp, allowLocalNetwork, sortOrder,
                hasNewEpisodes, latestEpisodeGuid, latestEpisodePubDate,
                isLatestEpisodePlayed, lastModifiedHeader, eTagHeader
            ) VALUES (?, ?, 'Description', 'https://example.com/cover.jpg', 0,
                0, 0, 0, 0, ?, NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf(rssUrl, rssUrl, if (hasNewEpisodes) 1 else 0)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertEpisode(
        rssUrl: String,
        guid: String,
        isPlayed: Boolean
    ) {
        execSQL(
            """
            INSERT INTO episodes (
                guid, podcastRssUrl, title, description, pubDate, link, enclosureUrl,
                type, fileSize, isPlayed, playbackPositionMs, downloadStatus,
                downloadPath, isFavorite, datePlayed, favoriteTimestamp,
                favoriteAddedAt, duration
            ) VALUES (?, ?, ?, 'Description', 2000, 'https://example.com/episode',
                'https://example.com/episode.mp3', 'audio/mpeg', 42, ?, 0,
                'NOT_DOWNLOADED', NULL, 0, NULL, NULL, NULL, 60000)
            """.trimIndent(),
            arrayOf(guid, rssUrl, guid, if (isPlayed) 1 else 0)
        )
    }

    private fun assertPodcastBadgeState(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        rssUrl: String,
        hasNewEpisodes: Int,
        lastSeenEpisodeGuid: String?,
        isLatestEpisodePlayed: Int?
    ) {
        database.query(
            """
            SELECT hasNewEpisodes, lastSeenEpisodeGuid, isLatestEpisodePlayed
            FROM podcasts WHERE rssUrl = ?
            """.trimIndent(),
            arrayOf(rssUrl)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(hasNewEpisodes, cursor.getInt(0))
            if (lastSeenEpisodeGuid == null) {
                assertTrue(cursor.isNull(1))
            } else {
                assertEquals(lastSeenEpisodeGuid, cursor.getString(1))
            }
            if (isLatestEpisodePlayed == null) {
                assertTrue(cursor.isNull(2))
            } else {
                assertEquals(isLatestEpisodePlayed, cursor.getInt(2))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "podcast-last-seen-migration"
        const val UNPLAYED_FEED = "https://example.com/unplayed.xml"
        const val UNPLAYED_GUID = "unplayed-latest"
        const val PLAYED_FEED = "https://example.com/played.xml"
        const val PLAYED_GUID = "played-latest"
        const val EMPTY_FEED = "https://example.com/empty.xml"
    }
}
