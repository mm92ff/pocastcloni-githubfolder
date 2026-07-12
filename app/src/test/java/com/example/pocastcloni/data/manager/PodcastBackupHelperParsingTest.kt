package com.example.pocastcloni.data.manager

import com.example.pocastcloni.data.local.BackupData
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastBackupHelperParsingTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerKotlinModule()
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

    @Test
    fun parseBackupJson_acceptsCurrentBackupFormat() {
        val result =
            parseBackupJson(
                """
                {
                  "version": 1,
                  "podcasts": [
                    {
                      "url": "https://example.com/feed.xml",
                      "sortOrder": 1
                    }
                  ]
                }
                """.trimIndent(),
                objectMapper
            )

        assertEquals(1, result.podcasts.size)
        assertEquals("https://example.com/feed.xml", result.podcasts.first().url)
    }

    @Test
    fun parseBackupJson_acceptsLegacyUrlList() {
        val result =
            parseBackupJson(
                """
                [
                  "https://example.com/feed-a.xml",
                  "https://example.com/feed-b.xml"
                ]
                """.trimIndent(),
                objectMapper
            )

        assertEquals(2, result.podcasts.size)
        assertEquals(0L, result.podcasts[0].sortOrder)
        assertEquals(1L, result.podcasts[1].sortOrder)
    }

    @Test
    fun parseBackupJson_acceptsLegacyLowercaseIndicatorOffsets() {
        val result =
            parseBackupJson(
                """
                {
                  "version": 1,
                  "settings": {
                    "indicator": {
                      "colorArgb": 4283215696,
                      "size": 22,
                      "borderWidth": 7,
                      "xoffset": 9,
                      "yoffset": 11
                    }
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertEquals(9, result.settings?.indicator?.xOffset)
        assertEquals(11, result.settings?.indicator?.yOffset)
    }

    @Test
    fun exportBackupJson_writesCanonicalIndicatorOffsetNames() {
        val backupData =
            BackupData(
                settings =
                UserSettings(
                    indicator =
                    IndicatorSettings(
                        xOffset = 9,
                        yOffset = 11
                    )
                )
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"xOffset\":9"))
        assertTrue(json.contains("\"yOffset\":11"))
        assertFalse(json.contains("\"xoffset\""))
        assertFalse(json.contains("\"yoffset\""))
    }

    @Test
    fun exportBackupJson_writesMiniPlayerTimeOverlaySetting() {
        val backupData =
            BackupData(
                settings = UserSettings(showMiniPlayerTimeOverlay = true)
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"showMiniPlayerTimeOverlay\":true"))
    }

    @Test
    fun exportBackupJson_writesBottomBarCleanModeSetting() {
        val backupData =
            BackupData(
                settings = UserSettings(bottomBarCleanModeEnabled = true)
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"bottomBarCleanModeEnabled\":true"))
    }

    @Test
    fun exportBackupJson_writesBottomBarAutoHideSettings() {
        val backupData =
            BackupData(
                settings =
                UserSettings(
                    bottomBarAutoHideEnabled = true,
                    bottomBarAutoHideDelaySeconds = 9
                )
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"bottomBarAutoHideEnabled\":true"))
        assertTrue(json.contains("\"bottomBarAutoHideDelaySeconds\":9"))
    }

    @Test
    fun exportBackupJson_writesGradientBackgroundSetting() {
        val backupData =
            BackupData(
                settings =
                UserSettings(
                    gradientBackgroundEnabled = true,
                    gradientBackgroundStrength = 0.4f
                )
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"gradientBackgroundEnabled\":true"))
        assertTrue(json.contains("\"gradientBackgroundStrength\":0.4"))
    }

    @Test
    fun exportBackupJson_writesTransparentSearchCardsSetting() {
        val backupData =
            BackupData(
                settings = UserSettings(transparentSearchCards = true)
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"transparentSearchCards\":true"))
    }

    @Test
    fun exportBackupJson_writesTransparentEpisodeRowsSetting() {
        val backupData =
            BackupData(
                settings = UserSettings(transparentEpisodeRows = true)
            )

        val json = objectMapper.writeValueAsString(backupData)

        assertTrue(json.contains("\"transparentEpisodeRows\":true"))
    }

    @Test
    fun exportAndParseBackupJson_roundTripsAllCurrentUserSettings() {
        val expectedSettings =
            UserSettings(
                theme = AppTheme.DARK,
                appColor = AppColor.RED,
                colorStrength = 0.5f,
                bufferMode = BufferMode.MAXIMAL,
                layoutMode = LayoutMode.LIST,
                gridSize = 4,
                showGridTitles = false,
                confirmDelete = false,
                progressBarHeight = 9,
                navBarHeight = 88,
                showMiniPlayerTimeOverlay = true,
                transparentMiniPlayer = true,
                transparentBottomBar = true,
                oneHandedMode = true,
                bottomBarCleanModeEnabled = true,
                bottomBarAutoHideEnabled = true,
                bottomBarAutoHideDelaySeconds = 8,
                gradientBackgroundEnabled = true,
                gradientBackgroundStrength = 0.75f,
                gradientBackgroundDirection = GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT,
                transparentSearchCards = true,
                transparentPodcastCards = true,
                transparentEpisodeRows = true,
                autoDownloadLimit = 6,
                autoRefreshOnStart = false,
                backgroundCheckEnabled = false,
                backgroundCheckInterval = 12,
                markPlayedDurationSeconds = 45,
                feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                indicator =
                    IndicatorSettings(
                        colorArgb = 0xFF112233,
                        size = 24,
                        borderWidth = 3,
                        xOffset = 10,
                        yOffset = -6
                    ),
                saveToDownloadsFolder = true,
                autoCleanupEnabled = true,
                cleanupKeepLimit = 42,
                cleanupIntervalHours = 36
            )
        val json = objectMapper.writeValueAsString(BackupData(settings = expectedSettings))

        val restoredSettings = parseBackupJson(json, objectMapper).settings

        assertEquals(expectedSettings, restoredSettings)
    }

    @Test
    fun parseBackupJson_defaultsMissingMiniPlayerTimeOverlaySetting() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.showMiniPlayerTimeOverlay ?: true)
    }

    @Test
    fun parseBackupJson_defaultsMissingBottomBarCleanModeSetting() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.bottomBarCleanModeEnabled ?: true)
    }

    @Test
    fun parseBackupJson_defaultsMissingBottomBarAutoHideSettings() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.bottomBarAutoHideEnabled ?: true)
        assertEquals(5, result.settings?.bottomBarAutoHideDelaySeconds)
    }

    @Test
    fun parseBackupJson_defaultsMissingGradientBackgroundSetting() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.gradientBackgroundEnabled ?: true)
        assertEquals(1.0f, result.settings?.gradientBackgroundStrength ?: 0f, 0.001f)
    }

    @Test
    fun parseBackupJson_defaultsMissingTransparentSearchCardsSetting() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.transparentSearchCards ?: true)
    }

    @Test
    fun parseBackupJson_defaultsMissingTransparentEpisodeRowsSetting() {
        val result =
            parseBackupJson(
                """
                {
                  "settings": {
                    "progressBarHeight": 30
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        assertFalse(result.settings?.transparentEpisodeRows ?: true)
    }

    @Test
    fun parseBackupJson_rejectsBlankOrContentFreeBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            parseBackupJson("", objectMapper)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupData(BackupData())
        }
    }

    @Test
    fun parseBackupJson_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            parseBackupJson("""{"podcasts":"not-a-list"}""", objectMapper)
        }
    }

    @Test
    fun parseBackupJson_rejectsUnsupportedOrCredentialedPodcastUrls() {
        listOf(
            "file:///data/local/feed.xml",
            "content://provider/feed",
            "https://user:secret@example.com/feed.xml"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                parseBackupJson(
                    """{"podcasts":[{"url":"$url"}]}""",
                    objectMapper
                )
            }
        }
    }

    @Test
    fun parseBackupJson_preservesButDoesNotGrantHttpMetadata() {
        val parsed = parseBackupJson(
            """{"podcasts":[{"url":"http://example.com/feed.xml","allow_insecure_http":true}]}""",
            objectMapper
        )
        assertTrue(parsed.podcasts.single().allowInsecureHttp)
    }
}
