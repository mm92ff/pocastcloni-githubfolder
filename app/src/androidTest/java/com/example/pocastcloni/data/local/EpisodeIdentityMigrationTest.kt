package com.example.pocastcloni.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.data.remote.RssEnclosure
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.repository.toEpisodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeIdentityMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration14To15PreservesStateAndScopesDuplicateGuidsToFeeds() {
        helper.createDatabase(DATABASE_NAME, 14).apply {
            insertPodcast(FEED_A, "Feed A")
            insertEpisode(FEED_A)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            15,
            true,
            AppDatabaseMigrations.MIGRATION_14_15
        )

        val originalId = migrated.query(
            "SELECT episodeId, isPlayed, playbackPositionMs, downloadStatus, downloadPath, " +
                "isFavorite, datePlayed, favoriteTimestamp, favoriteAddedAt " +
                "FROM episodes WHERE podcastRssUrl = ? AND guid = ?",
            arrayOf(FEED_A, SHARED_GUID)
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(1))
            assertEquals(12_345L, cursor.getLong(2))
            assertEquals("DOWNLOADED", cursor.getString(3))
            assertEquals("/tmp/episode.mp3", cursor.getString(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals(1_700_000_100_000L, cursor.getLong(6))
            assertEquals(1_700_000_200_000L, cursor.getLong(7))
            assertEquals(1_700_000_300_000L, cursor.getLong(8))
            cursor.getLong(0)
        }

        migrated.insertPodcast(FEED_B, "Feed B")
        migrated.insertEpisode(FEED_B)

        val secondId = migrated.query(
            "SELECT episodeId FROM episodes WHERE podcastRssUrl = ? AND guid = ?",
            arrayOf(FEED_B, SHARED_GUID)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

        assertNotEquals(originalId, secondId)
        migrated.insertEpisode(FEED_B, "post-migration-guid", "PostMigrationFresh")
        assertFtsCount(migrated, "PostMigrationFresh", 1)
        migrated.execSQL(
            "UPDATE episodes SET title = 'PostMigrationRenamed', description = 'SearchNeedle' " +
                "WHERE podcastRssUrl = ? AND guid = ?",
            arrayOf(FEED_B, "post-migration-guid")
        )
        assertFtsCount(migrated, "PostMigrationFresh", 0)
        assertFtsCount(migrated, "SearchNeedle", 1)
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        migrated.close()
    }

    @Test
    fun firstPostMigrationUpsertReusesLegacyTitleFallbackIdentity() = runBlocking {
        helper.createDatabase(FALLBACK_DATABASE_NAME, 14).apply {
            insertPodcast(FEED_A, "Feed A")
            insertLegacyTitleFallbackEpisode()
            close()
        }
        helper.runMigrationsAndValidate(
            FALLBACK_DATABASE_NAME,
            15,
            true,
            AppDatabaseMigrations.MIGRATION_14_15
        ).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, FALLBACK_DATABASE_NAME)
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .build()
        try {
            val incoming = RssItem(
                title = LEGACY_FALLBACK_TITLE,
                guid = null,
                link = null,
                enclosure = RssEnclosure(
                    url = "https://example.com/legacy-fallback.mp3",
                    type = "audio/mpeg",
                    length = 42L
                )
            ).toEpisodeEntity(FEED_A)

            database.podcastDao().upsertEpisodesEfficient(listOf(incoming))

            val episodes = database.podcastDao().getEpisodesForPodcastSync(FEED_A)
            assertEquals(1, episodes.size)
            assertEquals(LEGACY_FALLBACK_TITLE, episodes.single().guid)
            assertEquals(true, episodes.single().isFavorite)
            assertEquals(true, episodes.single().isPlayed)
            assertEquals(12_345L, episodes.single().playbackPositionMs)
            assertEquals(DownloadStatus.DOWNLOADED, episodes.single().downloadStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun firstPostMigrationUpsertPreservesBlankLegacyFallbacksByteForByte() = runBlocking {
        helper.createDatabase(BLANK_FALLBACK_DATABASE_NAME, 14).apply {
            insertPodcast(FEED_A, "Feed A")
            insertRawLegacyFallbackEpisode("", "Empty link identity", 111L)
            insertRawLegacyFallbackEpisode("   ", "   ", 222L)
            close()
        }
        helper.runMigrationsAndValidate(
            BLANK_FALLBACK_DATABASE_NAME,
            15,
            true,
            AppDatabaseMigrations.MIGRATION_14_15
        ).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            BLANK_FALLBACK_DATABASE_NAME
        ).addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS).build()
        try {
            val incoming = listOf(
                RssItem(
                    guid = null,
                    link = "",
                    title = "Empty link identity",
                    enclosure = RssEnclosure(url = "https://example.com/empty.mp3")
                ).toEpisodeEntity(FEED_A),
                RssItem(
                    guid = null,
                    link = null,
                    title = "   ",
                    enclosure = RssEnclosure(url = "https://example.com/whitespace.mp3")
                ).toEpisodeEntity(FEED_A)
            )

            database.podcastDao().upsertEpisodesEfficient(incoming)

            val episodes = database.podcastDao().getEpisodesForPodcastSync(FEED_A)
            assertEquals(2, episodes.size)
            assertEquals(111L, database.podcastDao().getEpisodeByFeedAndGuid(FEED_A, "")?.playbackPositionMs)
            assertEquals(
                222L,
                database.podcastDao().getEpisodeByFeedAndGuid(FEED_A, "   ")?.playbackPositionMs
            )
        } finally {
            database.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertPodcast(
        feedUrl: String,
        title: String
    ) {
        execSQL(
            """
            INSERT INTO podcasts (
                rssUrl, title, description, imageUrl, lastRefreshed,
                autoDownloadEnabled, allowInsecureHttp, allowLocalNetwork, sortOrder,
                hasNewEpisodes, latestEpisodeGuid, latestEpisodePubDate,
                isLatestEpisodePlayed, lastModifiedHeader, eTagHeader
            ) VALUES (?, ?, '', 'https://example.com/cover.png', 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf(feedUrl, title)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertEpisode(
        feedUrl: String,
        guid: String = SHARED_GUID,
        title: String = "Shared episode"
    ) {
        execSQL(
            """
            INSERT INTO episodes (
                guid, podcastRssUrl, title, description, pubDate, link, enclosureUrl,
                type, fileSize, isPlayed, playbackPositionMs, downloadStatus,
                downloadPath, isFavorite, datePlayed, favoriteTimestamp,
                favoriteAddedAt, duration
            ) VALUES (?, ?, ?, 'Description', 1700000000000,
                'https://example.com/episode', 'https://example.com/episode.mp3',
                'audio/mpeg', 42, 1, 12345, 'DOWNLOADED', '/tmp/episode.mp3',
                1, 1700000100000, 1700000200000, 1700000300000, 60000)
            """.trimIndent(),
            arrayOf(guid, feedUrl, title)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLegacyTitleFallbackEpisode() {
        execSQL(
            """
            INSERT INTO episodes (
                guid, podcastRssUrl, title, description, pubDate, link, enclosureUrl,
                type, fileSize, isPlayed, playbackPositionMs, downloadStatus,
                downloadPath, isFavorite, datePlayed, favoriteTimestamp,
                favoriteAddedAt, duration
            ) VALUES (?, ?, ?, 'Description', 1700000000000, '',
                'https://example.com/legacy-fallback.mp3', 'audio/mpeg', 42,
                1, 12345, 'DOWNLOADED', '/tmp/legacy-fallback.mp3',
                1, 1700000100000, 1700000200000, 1700000300000, 60000)
            """.trimIndent(),
            arrayOf(LEGACY_FALLBACK_TITLE, FEED_A, LEGACY_FALLBACK_TITLE)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertRawLegacyFallbackEpisode(
        guid: String,
        title: String,
        playbackPositionMs: Long
    ) {
        execSQL(
            """
            INSERT INTO episodes (
                guid, podcastRssUrl, title, description, pubDate, link, enclosureUrl,
                type, fileSize, isPlayed, playbackPositionMs, downloadStatus,
                downloadPath, isFavorite, datePlayed, favoriteTimestamp,
                favoriteAddedAt, duration
            ) VALUES (?, ?, ?, 'Description', 1700000000000, '',
                'https://example.com/raw-fallback.mp3', 'audio/mpeg', 42,
                1, ?, 'DOWNLOADED', '/tmp/raw-fallback.mp3',
                1, 1700000100000, 1700000200000, 1700000300000, 60000)
            """.trimIndent(),
            arrayOf(guid, FEED_A, title, playbackPositionMs)
        )
    }

    private fun assertFtsCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        query: String,
        expected: Int
    ) {
        database.query(
            "SELECT count(*) FROM episodes_fts WHERE episodes_fts MATCH ?",
            arrayOf(query)
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val DATABASE_NAME = "episode-identity-migration"
        const val FALLBACK_DATABASE_NAME = "episode-identity-fallback-migration"
        const val BLANK_FALLBACK_DATABASE_NAME = "episode-identity-blank-fallback-migration"
        const val FEED_A = "https://example.com/a.xml"
        const val FEED_B = "https://example.com/b.xml"
        const val SHARED_GUID = "shared-guid"
        const val LEGACY_FALLBACK_TITLE = "Legacy title identity"
    }
}
