package com.example.pocastcloni.data.manager

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.IndicatorSettings
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

    private val helper =
        PodcastBackupHelper(
            objectMapper =
            ObjectMapper().apply {
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
    fun exportAndImport_roundTripBackupThroughContentResolver() =
        runBlocking {
            val expectedSettings =
                UserSettings(
                    theme = AppTheme.DARK,
                    appColor = AppColor.TURQUOISE,
                    colorStrength = 0.65f,
                    bufferMode = BufferMode.MAXIMAL,
                    layoutMode = LayoutMode.LIST,
                    gridSize = 5,
                    showGridTitles = false,
                    confirmDelete = false,
                    progressBarHeight = 11,
                    navBarHeight = 92,
                    showMiniPlayerTimeOverlay = true,
                    transparentMiniPlayer = true,
                    transparentBottomBar = true,
                    oneHandedMode = true,
                    bottomBarCleanModeEnabled = true,
                    bottomBarAutoHideEnabled = true,
                    bottomBarAutoHideDelaySeconds = 9,
                    gradientBackgroundEnabled = true,
                    gradientBackgroundStrength = 0.8f,
                    gradientBackgroundDirection = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT,
                    transparentSearchCards = true,
                    transparentPodcastCards = true,
                    transparentEpisodeRows = true,
                    autoDownloadLimit = 7,
                    autoRefreshOnStart = false,
                    backgroundCheckEnabled = false,
                    backgroundCheckInterval = 12,
                    markPlayedDurationSeconds = 45,
                    feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                    indicator =
                    IndicatorSettings(
                        colorArgb = 0xFF123456,
                        size = 23,
                        borderWidth = 4,
                        xOffset = 13,
                        yOffset = -8
                    ),
                    saveToDownloadsFolder = true,
                    autoCleanupEnabled = true,
                    cleanupKeepLimit = 37,
                    cleanupIntervalHours = 48
                )

            helper.exportBackup(
                podcasts =
                listOf(
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
                episodeStates =
                listOf(
                    BackupEpisodeState(
                        podcastUrl = "https://example.com/feed.xml",
                        episodeGuid = "episode-1",
                        title = "Episode 1",
                        isFavorite = true,
                        favoriteAddedAt = 123L,
                        favoriteOrder = 0
                    )
                ),
                settings = expectedSettings,
                uri = backupFile.toUri(),
                contentResolver = context.contentResolver
            )

            val restored = helper.importBackup(backupFile.toUri(), context.contentResolver)

            assertEquals(1, restored.podcasts.size)
            assertEquals("https://example.com/feed.xml", restored.podcasts.first().url)
            assertEquals(1, restored.episodeStates.size)
            assertEquals("episode-1", restored.episodeStates.first().episodeGuid)
            assertEquals(expectedSettings, restored.settings)
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
