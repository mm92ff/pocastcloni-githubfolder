package com.example.pocastcloni.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrateLegacyVersion9_preservesPodcastAndEpisodeData() {
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `podcasts` (
                    `rssUrl` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `imageUrl` TEXT NOT NULL,
                    `lastRefreshed` INTEGER NOT NULL,
                    `autoDownloadEnabled` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `hasNewEpisodes` INTEGER NOT NULL,
                    `lastModifiedHeader` TEXT,
                    `eTagHeader` TEXT,
                    PRIMARY KEY(`rssUrl`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `episodes` (
                    `guid` TEXT NOT NULL,
                    `podcastRssUrl` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `pubDate` INTEGER,
                    `link` TEXT NOT NULL,
                    `enclosureUrl` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `fileSize` INTEGER NOT NULL,
                    `isPlayed` INTEGER NOT NULL,
                    `playbackPositionMs` INTEGER NOT NULL,
                    `downloadStatus` TEXT NOT NULL,
                    `downloadPath` TEXT,
                    `isFavorite` INTEGER NOT NULL,
                    `datePlayed` INTEGER,
                    `favoriteTimestamp` INTEGER,
                    PRIMARY KEY(`guid`),
                    FOREIGN KEY(`podcastRssUrl`) REFERENCES `podcasts`(`rssUrl`) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            version = 9
            execSQL(
                """
                INSERT INTO `podcasts` (
                    `rssUrl`,
                    `title`,
                    `description`,
                    `imageUrl`,
                    `lastRefreshed`,
                    `autoDownloadEnabled`,
                    `sortOrder`,
                    `hasNewEpisodes`,
                    `lastModifiedHeader`,
                    `eTagHeader`
                ) VALUES (
                    'https://example.com/feed.xml',
                    'Example Podcast',
                    'Legacy description',
                    'http://example.com/cover.png',
                    1700000000000,
                    1,
                    7,
                    1,
                    'Sat, 02 Mar 2024 10:00:00 GMT',
                    'etag-1'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `episodes` (
                    `guid`,
                    `podcastRssUrl`,
                    `title`,
                    `description`,
                    `pubDate`,
                    `link`,
                    `enclosureUrl`,
                    `type`,
                    `fileSize`,
                    `isPlayed`,
                    `playbackPositionMs`,
                    `downloadStatus`,
                    `downloadPath`,
                    `isFavorite`,
                    `datePlayed`,
                    `favoriteTimestamp`
                ) VALUES (
                    'episode-1',
                    'https://example.com/feed.xml',
                    'Legacy Episode',
                    'Legacy episode description',
                    1700000100000,
                    'https://example.com/episodes/1',
                    'http://example.com/audio/1.mp3',
                    'audio/mpeg',
                    12345,
                    0,
                    3210,
                    'DOWNLOADED',
                    '/tmp/example.mp3',
                    1,
                    NULL,
                    1700000200000
                )
                """.trimIndent()
            )
            close()
        }

        val db =
            Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
                .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
                .build()

        db.openHelper.writableDatabase

        runBlocking {
            val podcast = db.podcastDao().getPodcastByUrl("https://example.com/feed.xml")
            assertNotNull(podcast)
            assertEquals("Example Podcast", podcast?.title)
            assertEquals("episode-1", podcast?.latestEpisodeGuid)
            assertEquals("etag-1", podcast?.eTagHeader)
            assertEquals(true, podcast?.allowInsecureHttp)

            val episode = db.podcastDao().getEpisodeByGuid("episode-1")
            assertNotNull(episode)
            assertEquals(0L, episode?.duration)
            assertEquals(3210L, episode?.playbackPositionMs)
            assertEquals(1700000200000L, episode?.favoriteAddedAt)
        }

        db.openHelper.writableDatabase.query(
            "SELECT count(*) FROM `episodes_fts` WHERE `episodes_fts` MATCH 'Legacy'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        db.close()
    }

    companion object {
        private const val TEST_DB = "app-database-migration-test"
    }
}
