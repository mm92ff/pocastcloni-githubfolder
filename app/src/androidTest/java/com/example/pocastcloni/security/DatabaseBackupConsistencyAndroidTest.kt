package com.example.pocastcloni.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBackupConsistencyAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        listOf(SOURCE_DB, RESTORED_DB).forEach(context::deleteDatabase)
    }

    @Test
    fun closedWalDatabaseCanBeRestoredFromMainFileOnly() {
        val source = context.getDatabasePath(SOURCE_DB)
        source.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
            database.enableWriteAheadLogging()
            database.execSQL("CREATE TABLE podcasts (rssUrl TEXT PRIMARY KEY, title TEXT NOT NULL)")
            database.execSQL(
                "INSERT INTO podcasts (rssUrl, title) VALUES (?, ?)",
                arrayOf("https://example.com/feed.xml", "Example")
            )
        }

        assertEquals(false, File("${source.path}-wal").exists())
        assertEquals(false, File("${source.path}-shm").exists())
        val restored = context.getDatabasePath(RESTORED_DB)
        source.copyTo(restored, overwrite = true)

        SQLiteDatabase.openDatabase(restored.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT rssUrl, title FROM podcasts", null).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("https://example.com/feed.xml", cursor.getString(0))
                assertEquals("Example", cursor.getString(1))
                assertEquals(false, cursor.moveToNext())
            }
        }
    }

    private companion object {
        const val SOURCE_DB = "security-backup-source.db"
        const val RESTORED_DB = "security-backup-restored.db"
    }
}
