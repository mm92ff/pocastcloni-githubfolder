package com.example.pocastcloni.data.manager

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PodcastBackupHelperAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val helper = PodcastBackupHelper(
        objectMapper = ObjectMapper().apply {
            registerKotlinModule()
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        },
        dispatcherProvider = DefaultDispatcherProvider()
    )

    private val backupFile = File(context.cacheDir, "podcast-backup-helper-test.json")

    @Before
    fun setUp() {
        backupFile.delete()
    }

    @After
    fun tearDown() {
        backupFile.delete()
    }

    @Test
    fun exportAndImport_roundTripBackupThroughContentResolver() = runBlocking {
        helper.exportBackup(
            podcasts = listOf(
                BackupPodcast(
                    url = "https://example.com/feed.xml",
                    sortOrder = 1,
                    title = "Example Podcast",
                    description = "Description",
                    imageUrl = "https://example.com/image.png",
                    lastModifiedHeader = "Sat, 02 Mar 2024 10:00:00 GMT",
                    eTagHeader = "etag-1"
                )
            ),
            favorites = listOf(
                BackupFavorite(
                    podcastUrl = "https://example.com/feed.xml",
                    episodeGuid = "episode-1",
                    timestamp = 123L
                )
            ),
            settings = UserSettings(autoDownloadLimit = 4),
            uri = backupFile.toUri(),
            contentResolver = context.contentResolver
        )

        val restored = helper.importBackup(backupFile.toUri(), context.contentResolver)

        assertEquals(1, restored.podcasts.size)
        assertEquals("https://example.com/feed.xml", restored.podcasts.first().url)
        assertEquals(1, restored.favorites.size)
        assertEquals("episode-1", restored.favorites.first().episodeGuid)
        assertEquals(4, restored.settings?.autoDownloadLimit)
    }

    @Test
    fun importBackup_rejectsInvalidBackupFile() {
        backupFile.writeText("""{"podcasts":"not-a-list"}""")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                helper.importBackup(backupFile.toUri(), context.contentResolver)
            }
        }
    }
}
