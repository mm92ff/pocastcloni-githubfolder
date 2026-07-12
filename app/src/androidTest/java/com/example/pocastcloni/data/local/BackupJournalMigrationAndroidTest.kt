package com.example.pocastcloni.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupJournalMigrationAndroidTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration12To13PreservesLibraryAndCreatesEmptyJournal() {
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                """
                INSERT INTO podcasts (
                    rssUrl, title, description, imageUrl, lastRefreshed,
                    autoDownloadEnabled, allowInsecureHttp, sortOrder, hasNewEpisodes
                ) VALUES ('https://example.com/feed.xml', 'Existing', '', '', 0, 0, 0, 1, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO episodes (
                    guid, podcastRssUrl, title, description, link, enclosureUrl,
                    type, fileSize, isPlayed, playbackPositionMs, downloadStatus,
                    isFavorite, duration
                ) VALUES (
                    'episode-1', 'https://example.com/feed.xml', 'Episode', '',
                    'https://example.com/episode-1', 'https://example.com/episode-1.mp3',
                    'audio/mpeg', 10, 1, 1234, 'DOWNLOADED', 1, 60000
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            13,
            true,
            AppDatabaseMigrations.MIGRATION_12_13
        )

        database.query("SELECT title FROM podcasts").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Existing", cursor.getString(0))
        }
        database.query("SELECT downloadStatus, isFavorite FROM episodes").use { cursor ->
            cursor.moveToFirst()
            assertEquals("DOWNLOADED", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        database.query("SELECT count(*) FROM backup_import_journal").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    fun migration13To14PreservesLibraryAndDefaultsLocalApprovalToFalse() {
        helper.createDatabase(LOCAL_APPROVAL_TEST_DB, 13).apply {
            execSQL(
                """
                INSERT INTO podcasts (
                    rssUrl, title, description, imageUrl, lastRefreshed,
                    autoDownloadEnabled, allowInsecureHttp, sortOrder, hasNewEpisodes
                ) VALUES ('https://example.com/feed.xml', 'Existing', '', '', 0, 0, 1, 1, 0)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            LOCAL_APPROVAL_TEST_DB,
            14,
            true,
            AppDatabaseMigrations.MIGRATION_13_14
        )

        database.query(
            "SELECT title, allowInsecureHttp, allowLocalNetwork FROM podcasts"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Existing", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        database.close()
    }

    private companion object {
        const val TEST_DB = "backup-journal-migration-test"
        const val LOCAL_APPROVAL_TEST_DB = "local-approval-migration-test"
    }
}
