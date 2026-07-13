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
class FeedValidatorMigrationAndroidTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun migration15To16ClearsOnlyPodcastValidators() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                """
                INSERT INTO podcasts (
                    rssUrl, title, description, imageUrl, lastRefreshed,
                    autoDownloadEnabled, allowInsecureHttp, allowLocalNetwork,
                    sortOrder, hasNewEpisodes, lastModifiedHeader, eTagHeader
                ) VALUES (
                    'https://example.com/feed.xml', 'Existing podcast', 'Description',
                    'https://example.com/cover.jpg', 1234, 1, 0, 0, 7, 1,
                    'legacy-last-modified', 'legacy-etag'
                )
                """.trimIndent()
            )
            close()
        }

        val database =
            helper.runMigrationsAndValidate(
                TEST_DB,
                16,
                true,
                AppDatabaseMigrations.MIGRATION_15_16
            )

        database.query(
            "SELECT title, description, sortOrder, hasNewEpisodes, lastModifiedHeader, eTagHeader FROM podcasts"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing podcast", cursor.getString(0))
            assertEquals("Description", cursor.getString(1))
            assertEquals(7L, cursor.getLong(2))
            assertEquals(1, cursor.getInt(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
        }
        database.close()

        assertTrue(AppDatabaseMigrations.MIGRATION_15_16 in AppDatabaseMigrations.ALL_MIGRATIONS)
    }

    private companion object {
        const val TEST_DB = "feed-validator-migration-test"
    }
}
