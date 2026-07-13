package com.example.pocastcloni.data.manager

import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.settingsForRestore
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Parsing compatibility cases intentionally share one mapper and adjacent JSON fixtures.
@Suppress("LargeClass")
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
    fun `legacy backup validators remain readable for compatibility`() {
        val result = parseBackupJson(
            """
            {
              "version": 1,
              "podcasts": [{
                "url": "https://example.com/feed.xml",
                "last_modified": "legacy-last-modified",
                "etag": "legacy-etag"
              }]
            }
            """.trimIndent(),
            objectMapper
        )

        assertEquals("legacy-last-modified", result.podcasts.single().lastModifiedHeader)
        assertEquals("legacy-etag", result.podcasts.single().eTagHeader)
    }

    @Test
    fun `backup serialization never exports validators`() {
        val json = objectMapper.writeValueAsString(
            BackupData(
                podcasts = listOf(
                    BackupPodcast(
                        url = "https://example.com/feed.xml",
                        lastModifiedHeader = "private-last-modified",
                        eTagHeader = "private-etag"
                    )
                )
            )
        )

        assertFalse(json.contains("last_modified"))
        assertFalse(json.contains("etag"))
        assertFalse(json.contains("private-last-modified"))
        assertFalse(json.contains("private-etag"))
    }

    @Test
    fun parseBackupJson_treatsMissingVersionAsVersionOne() {
        val result = parseBackupJson(
            """
            {
              "podcasts": [
                {
                  "url": "https://example.com/versionless.xml",
                  "sortOrder": 0
                }
              ]
            }
            """.trimIndent(),
            objectMapper
        )

        assertEquals(1, result.version)
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
        assertEquals(1, result.version)
    }

    @Test
    fun parseBackupJson_acceptsVersionOneFixture() {
        val fixture = requireNotNull(javaClass.getResource("/audit/legacy-backup-v1.json")).readText()

        val result = parseBackupJson(fixture, objectMapper)

        assertEquals(1, result.version)
        assertEquals(2, result.podcasts.size)
        assertEquals("shared-guid-across-feeds", result.favorites.single().episodeGuid)
        assertTrue(result.episodeStates.isEmpty())
    }

    @Test
    fun parseBackupJson_keepsDuplicateGuidsSeparatedByFeedAndIgnoresDownloadFields() {
        val parsed = parseBackupJson(
            """
            {
              "version": 2,
              "podcasts": [
                {"url":"https://feed-a.example/rss","sortOrder":0,"autoDownloadEnabled":true},
                {"url":"https://feed-b.example/rss","sortOrder":1,"autoDownloadEnabled":false}
              ],
              "episodeStates": [
                {
                  "podcastUrl":"https://feed-a.example/rss",
                  "episodeGuid":"shared-guid",
                  "title":"Episode A",
                  "duration":120000,
                  "isFavorite":true,
                  "favoriteAddedAt":1000,
                  "favoriteOrder":0,
                  "isPlayed":true,
                  "datePlayed":2000,
                  "playbackPositionMs":90000,
                  "downloadPath":"/tampered/a.mp3",
                  "downloadStatus":"DOWNLOADED"
                },
                {
                  "podcastUrl":"https://feed-b.example/rss",
                  "episodeGuid":"shared-guid",
                  "title":"Episode B",
                  "duration":180000,
                  "isFavorite":false,
                  "isPlayed":false,
                  "playbackPositionMs":30000,
                  "download_path":"content://tampered/b",
                  "download_status":"DOWNLOADED"
                }
              ]
            }
            """.trimIndent(),
            objectMapper
        )

        assertEquals(2, parsed.episodeStates.size)
        assertEquals(
            setOf("https://feed-a.example/rss", "https://feed-b.example/rss"),
            parsed.episodeStates.map { it.podcastUrl }.toSet()
        )
        val reexported = objectMapper.writeValueAsString(parsed)
        assertFalse(reexported.contains("downloadPath"))
        assertFalse(reexported.contains("downloadStatus"))
        assertFalse(reexported.contains("download_path"))
        assertFalse(reexported.contains("download_status"))
    }

    @Test
    fun versionTwoEpisodeStateRoundTripPreservesPortableFieldsAndOrder() {
        val expected = listOf(
            BackupEpisodeState(
                podcastUrl = "https://feed-a.example/rss",
                episodeGuid = "episode-a",
                title = "Episode A",
                description = "Description A",
                publishedAt = 500L,
                duration = 10_000L,
                isFavorite = true,
                favoriteAddedAt = 1_000L,
                favoriteOrder = 0,
                isPlayed = true,
                datePlayed = 2_000L,
                playbackPositionMs = 9_000L
            ),
            BackupEpisodeState(
                podcastUrl = "https://feed-a.example/rss",
                episodeGuid = "episode-b",
                title = "Episode B",
                duration = 20_000L,
                isFavorite = true,
                favoriteAddedAt = 3_000L,
                favoriteOrder = 1,
                playbackPositionMs = 4_000L
            )
        )
        val source = BackupData(
            podcasts = listOf(
                BackupPodcast(
                    url = "https://feed-a.example/rss",
                    autoDownloadEnabled = true
                )
            ),
            settings = UserSettings(),
            episodeStates = expected
        )

        val parsed = parseBackupJson(objectMapper.writeValueAsString(source), objectMapper)

        assertEquals(Constants.Backup.BACKUP_VERSION, parsed.version)
        assertEquals(true, parsed.podcasts.single().autoDownloadEnabled)
        assertEquals(expected, parsed.episodeStates)
        assertEquals(listOf(0L, 1L), parsed.episodeStates.map { it.favoriteOrder })
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
    fun parseBackupJson_mergesOnlyFieldsPresentInLegacySettings() {
        val current =
            UserSettings(
                theme = AppTheme.DARK,
                appColor = AppColor.RED,
                backgroundCheckInterval = 24,
                feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                indicator =
                IndicatorSettings(
                    colorArgb = 0xFF112233,
                    size = 32,
                    borderWidth = 4,
                    xOffset = -5,
                    yOffset = 6
                )
            )
        val parsed =
            parseBackupJson(
                """
                {
                  "version": 1,
                  "settings": {
                    "theme": "LIGHT",
                    "indicator": {
                      "size": 22,
                      "xoffset": 9,
                      "yoffset": 11
                    }
                  }
                }
                """.trimIndent(),
                objectMapper
            )

        val restored = parsed.settingsForRestore(current)

        assertEquals(
            current.copy(
                theme = AppTheme.LIGHT,
                indicator = current.indicator.copy(size = 22, xOffset = 9, yOffset = 11)
            ),
            restored
        )
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

        val parsed = parseBackupJson(json, objectMapper)
        val restoredSettings = parsed.settingsForRestore(UserSettings())
        val reexportedJson = objectMapper.writeValueAsString(parsed)

        assertEquals(expectedSettings, restoredSettings)
        assertFalse(reexportedJson.contains("settingsFieldPresence"))
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

    @Test
    fun validateBackupData_rejectsUnsupportedVersionAndExcessiveCollections() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupData(
                BackupData(
                    version = Constants.Backup.BACKUP_VERSION + 1,
                    settings = UserSettings()
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupData(
                BackupData(
                    podcasts = List(Constants.SecurityLimits.MAX_BACKUP_PODCASTS + 1) {
                        BackupPodcast(url = "https://example.com/$it.xml")
                    }
                )
            )
        }
    }

    @Test
    fun validateBackupData_rejectsOversizedMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupData(
                BackupData(
                    podcasts = listOf(
                        BackupPodcast(
                            url = "https://example.com/feed.xml",
                            title = "x".repeat(Constants.SecurityLimits.MAX_TITLE_CHARS + 1)
                        )
                    )
                )
            )
        }
    }

    @Test
    fun prevalidateBackupJson_rejectsLargeArraysBeforeBinding() {
        val json = buildString {
            append("{\"podcasts\":[")
            repeat(Constants.SecurityLimits.MAX_BACKUP_PODCASTS + 1) { index ->
                if (index > 0) append(',')
                append("{\"url\":\"https://example.com/")
                append(index)
                append("\"}")
            }
            append("]}")
        }

        assertThrows(IllegalArgumentException::class.java) {
            prevalidateBackupJson(json, objectMapper)
        }
    }

    @Test
    fun prevalidateBackupJson_rejectsOversizedStringsAndNesting() {
        val longTitle = "x".repeat(Constants.SecurityLimits.MAX_TITLE_CHARS + 1)
        assertThrows(IllegalArgumentException::class.java) {
            prevalidateBackupJson("""{"podcasts":[{"title":"$longTitle"}]}""", objectMapper)
        }

        val deeplyNested = "[".repeat(101) + "0" + "]".repeat(101)
        assertThrows(IllegalArgumentException::class.java) {
            prevalidateBackupJson(deeplyNested, objectMapper)
        }
    }

    @Test
    fun prevalidateBackupJson_countsNullElementsAndLimitsLegacyUrls() {
        val manyNulls = buildString {
            append("{\"podcasts\":[")
            repeat(Constants.SecurityLimits.MAX_BACKUP_PODCASTS + 1) { index ->
                if (index > 0) append(',')
                append("null")
            }
            append("]}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            prevalidateBackupJson(manyNulls, objectMapper)
        }

        val longLegacyUrl = "x".repeat(Constants.SecurityLimits.MAX_URL_CHARS + 1)
        assertThrows(IllegalArgumentException::class.java) {
            prevalidateBackupJson("[\"$longLegacyUrl\"]", objectMapper)
        }
    }
}
