package com.example.pocastcloni.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastCoverStateMigrationAndroidTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun migration16To17SeedsPendingCoverWithoutClaimingAFile() {
        helper.createDatabase(TEST_DATABASE, 16).apply {
            execSQL(
                """
                INSERT INTO podcasts (
                    rssUrl, title, description, imageUrl, lastRefreshed,
                    autoDownloadEnabled, allowInsecureHttp, allowLocalNetwork,
                    sortOrder, hasNewEpisodes
                ) VALUES (
                    'https://example.com/feed.xml', 'Podcast', 'Description',
                    '  https://example.com/cover.jpg  ', 1234, 0, 0, 0, 0, 0
                )
                """.trimIndent()
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DATABASE,
                17,
                true,
                AppDatabaseMigrations.MIGRATION_16_17
            )

        migrated.query(
            """
            SELECT activeSourceUrl, pendingSourceUrl, thumbnailFileName,
                   thumbnailRevision, failureCount
            FROM podcast_cover_state
            WHERE podcastRssUrl = 'https://example.com/feed.xml'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals("https://example.com/cover.jpg", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(0L, cursor.getLong(3))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "podcast-cover-state-migration"
    }
}
