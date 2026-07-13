package com.example.pocastcloni.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SupportedSchemaMigrationAndroidTest(
    private val startVersion: Int
) {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )

    @Test
    fun authenticSchemaMigratesToCurrentAndPreservesLibraryState() {
        val databaseName = "supported-schema-$startVersion-to-$CURRENT_SCHEMA"
        helper.createDatabase(databaseName, startVersion).apply {
            seedPodcastAndEpisode()
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                databaseName,
                CURRENT_SCHEMA,
                true,
                *AppDatabaseMigrations.ALL_MIGRATIONS
            )

        migrated.query(
            "SELECT title, autoDownloadEnabled, allowInsecureHttp, allowLocalNetwork, " +
                "sortOrder, lastModifiedHeader, eTagHeader FROM podcasts WHERE rssUrl = ?",
            arrayOf(FEED_URL)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Supported schema $startVersion", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(7L, cursor.getLong(4))
            assertTrue(cursor.isNull(5))
            assertTrue(cursor.isNull(6))
        }
        migrated.query(
            "SELECT guid, isPlayed, playbackPositionMs, downloadStatus, isFavorite, " +
                "favoriteAddedAt, duration FROM episodes WHERE podcastRssUrl = ? AND guid = ?",
            arrayOf(FEED_URL, EPISODE_GUID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(EPISODE_GUID, cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(12_345L, cursor.getLong(2))
            assertEquals("DOWNLOADED", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(FAVORITE_TIMESTAMP, cursor.getLong(5))
            assertEquals(60_000L, cursor.getLong(6))
        }
        migrated.query("SELECT count(*) FROM backup_import_journal").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA index_list(`episodes`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            var foundScopedIdentity = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "index_episodes_podcastRssUrl_guid") {
                    foundScopedIdentity = cursor.getInt(uniqueIndex) == 1
                }
            }
            assertTrue(foundScopedIdentity)
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        migrated.query(
            "SELECT count(*) FROM episodes_fts WHERE episodes_fts MATCH 'SupportedNeedle'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.seedPodcastAndEpisode() {
        val podcastColumns = columns("podcasts")
        val podcast =
            ContentValues().apply {
                put("rssUrl", FEED_URL)
                put("title", "Supported schema $startVersion")
                put("description", "Migration fixture")
                put("imageUrl", "http://legacy.example/cover.png")
                put("lastRefreshed", 1_700_000_000_000L)
                put("autoDownloadEnabled", 1)
                put("sortOrder", 7L)
                put("hasNewEpisodes", 1)
                put("latestEpisodeGuid", EPISODE_GUID)
                put("latestEpisodePubDate", 1_700_000_100_000L)
                put("isLatestEpisodePlayed", 1)
                put("lastModifiedHeader", "legacy-last-modified")
                put("eTagHeader", "legacy-etag")
                if ("allowInsecureHttp" in podcastColumns) put("allowInsecureHttp", 1)
                if ("allowLocalNetwork" in podcastColumns) put("allowLocalNetwork", 0)
            }
        assertTrue(insert("podcasts", SQLiteDatabase.CONFLICT_ABORT, podcast) >= 0)

        val episodeColumns = columns("episodes")
        val episode =
            ContentValues().apply {
                put("guid", EPISODE_GUID)
                put("podcastRssUrl", FEED_URL)
                put("title", "SupportedNeedle")
                put("description", "Migration episode")
                put("pubDate", 1_700_000_100_000L)
                put("link", "http://legacy.example/episode")
                put("enclosureUrl", "http://legacy.example/episode.mp3")
                put("type", "audio/mpeg")
                put("fileSize", 42L)
                put("isPlayed", 1)
                put("playbackPositionMs", 12_345L)
                put("downloadStatus", "DOWNLOADED")
                put("downloadPath", "/legacy/episode.mp3")
                put("isFavorite", 1)
                put("datePlayed", 1_700_000_200_000L)
                put("favoriteTimestamp", FAVORITE_TIMESTAMP)
                put("duration", 60_000L)
                if ("favoriteAddedAt" in episodeColumns) {
                    put("favoriteAddedAt", FAVORITE_TIMESTAMP)
                }
            }
        assertTrue(insert("episodes", SQLiteDatabase.CONFLICT_ABORT, episode) >= 0)
    }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    companion object {
        private const val CURRENT_SCHEMA = Constants.Database.DATABASE_VERSION
        private const val FEED_URL = "http://legacy.example/feed.xml"
        private const val EPISODE_GUID = "supported-migration-episode"
        private const val FAVORITE_TIMESTAMP = 1_700_000_300_000L

        @JvmStatic
        @Parameterized.Parameters(name = "schema {0} to $CURRENT_SCHEMA")
        fun supportedSchemas(): Iterable<Array<Int>> =
            (AppDatabaseMigrations.SUPPORTED_SCHEMA_FLOOR until CURRENT_SCHEMA).map { arrayOf(it) }
    }
}
