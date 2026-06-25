package com.example.pocastcloni.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.repository.UserPreferencesRepositoryImpl
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.service.PodcastPlaybackService
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainUserJourneyTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        runBlocking { resetAppState() }
        server =
            MockWebServer().apply {
                dispatcher = IntegrationFeedDispatcher()
                start()
            }
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { resetAppState() }
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun settingsImport_drivesCoreUserJourney() {
        scenario =
            ActivityScenario.launch(MainActivity::class.java).also {
                it.moveToState(Lifecycle.State.RESUMED)
            }

        waitForText(HOME_EMPTY_TEXT)

        clickBottomNav(NAV_SETTINGS)
        waitForText(SETTINGS_TITLE)

        composeRule.onNode(hasSetTextAction()).performTextInput(server.url(FEED_PATH).toString())
        composeRule.onNodeWithText(ADD_BUTTON).performClick()

        waitForText(ADD_SUCCESS_TEXT, timeoutMillis = 20_000)
        waitForEpisodeRow { episode ->
            episode.guid == TEST_EPISODE_GUID && episode.podcastRssUrl == server.url(FEED_PATH).toString()
        }

        clickBottomNav(NAV_HOME)
        waitForText(TEST_PODCAST_TITLE)
        composeRule.onAllNodesWithText(TEST_PODCAST_TITLE).onFirst().performClick()

        waitForText(TEST_EPISODE_TITLE)
        composeRule.onNodeWithContentDescription(TOGGLE_FAVORITE).performClick()
        composeRule.onNodeWithContentDescription(TOGGLE_PLAYED).performClick()
        waitForEpisodeRow { episode -> episode.guid == TEST_EPISODE_GUID && episode.isFavorite && episode.isPlayed }

        clickBottomNav(NAV_HOME)
        waitForText(HOME_TITLE)
        composeRule.onNodeWithContentDescription(FAVORITES).performClick()
        waitForText(TEST_EPISODE_TITLE)
        composeRule.onNodeWithContentDescription(BACK).performClick()

        waitForText(HOME_TITLE)
        composeRule.onNodeWithContentDescription(HISTORY).performClick()
        waitForText(TEST_EPISODE_TITLE)
        composeRule.onNodeWithContentDescription(BACK).performClick()

        waitForText(HOME_TITLE)
        composeRule.onAllNodesWithText(TEST_PODCAST_TITLE).onFirst().performClick()

        composeRule.onNodeWithContentDescription(PLAY_PAUSE).performClick()
        waitUntil(
            timeoutMillis = 20_000,
            condition = { hasNodeWithContentDescription(MINI_PLAYER_PLAY) || hasNodeWithContentDescription(MINI_PLAYER_PAUSE) }
        )
        val miniPlayerDescription = if (hasNodeWithContentDescription(MINI_PLAYER_PAUSE)) MINI_PLAYER_PAUSE else MINI_PLAYER_PLAY
        composeRule.onNodeWithContentDescription(miniPlayerDescription, useUnmergedTree = true).performClick()

        composeRule.onNodeWithContentDescription(DOWNLOAD_EPISODE).performClick()
        waitForDownloadStatus(TEST_EPISODE_GUID, DownloadStatus.DOWNLOADED)
        waitUntil(timeoutMillis = 20_000) { hasNodeWithContentDescription(EPISODE_DOWNLOADED) }

        clickBottomNav(NAV_DOWNLOADS)
        waitForText(TEST_EPISODE_TITLE)

        clickBottomNav(NAV_HOME)
        waitForText(TEST_PODCAST_TITLE)
        composeRule.onAllNodesWithText(TEST_PODCAST_TITLE).onFirst().performClick()
        waitUntil(timeoutMillis = 20_000) { hasNodeWithContentDescription(EPISODE_DOWNLOADED) }
        composeRule.onNodeWithContentDescription(EPISODE_DOWNLOADED).performClick()
        waitForDownloadStatus(TEST_EPISODE_GUID, DownloadStatus.NOT_DOWNLOADED)

        clickBottomNav(NAV_DOWNLOADS)
        waitForText(NO_DOWNLOADS_TEXT)
    }

    private suspend fun resetAppState() {
        context.stopService(Intent(context, PodcastPlaybackService::class.java))
        WorkManager.getInstance(context).cancelAllWork().result.get()
        WorkManager.getInstance(context).pruneWork().result.get()

        val database = AppDatabase.getDatabase(context)
        database.podcastDao().deleteAllPodcasts()
        database.podcastDao().clearHistory()

        UserPreferencesRepositoryImpl(context).restoreSettings(
            UserSettings(
                layoutMode = LayoutMode.LIST,
                autoRefreshOnStart = false,
                backgroundCheckEnabled = false,
                feedUpdateMode = FeedUpdateMode.ALWAYS_FULL
            )
        )

        val downloadsDir = File(context.filesDir, Constants.DOWNLOADS_DIR)
        downloadsDir.listFiles()?.forEach { file -> file.delete() }
    }

    private fun waitForText(
        text: String,
        timeoutMillis: Long = 10_000
    ) {
        waitUntil(timeoutMillis) { hasNodeWithText(text) }
    }

    private fun clickBottomNav(label: String) {
        when {
            hasNodeWithText(label) -> composeRule.onAllNodesWithText(label).onFirst().performClick()
            hasNodeWithContentDescription(label) ->
                composeRule.onAllNodes(
                    hasContentDescription(label),
                    useUnmergedTree = true
                ).onFirst().performClick()
            else -> error("No bottom navigation node found for '$label'")
        }
    }

    private fun waitForEpisodeRow(
        timeoutMillis: Long = 20_000,
        predicate: (com.example.pocastcloni.data.local.EpisodeEntity) -> Boolean
    ) {
        waitUntil(timeoutMillis) {
            val episode = runBlocking { AppDatabase.getDatabase(context).podcastDao().getEpisodeByGuid(TEST_EPISODE_GUID) }
            episode != null && predicate(episode)
        }
    }

    private fun waitForDownloadStatus(
        guid: String,
        expected: DownloadStatus,
        timeoutMillis: Long = 20_000
    ) {
        waitUntil(timeoutMillis) {
            val episode = runBlocking { AppDatabase.getDatabase(context).podcastDao().getEpisodeByGuid(guid) }
            episode?.downloadStatus == expected
        }
    }

    private fun waitUntil(
        timeoutMillis: Long,
        condition: () -> Boolean
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching(condition).getOrDefault(false)
        }
    }

    private fun hasNodeWithText(text: String): Boolean {
        return composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun hasNodeWithContentDescription(description: String): Boolean {
        return composeRule.onAllNodes(hasContentDescription(description), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private inner class IntegrationFeedDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            return when (request.path) {
                FEED_PATH ->
                    MockResponse()
                        .setHeader("Content-Type", "application/rss+xml")
                        .setBody(feedXml())
                COVER_PATH ->
                    MockResponse()
                        .setHeader("Content-Type", "image/png")
                        .setBody(Buffer().write(TINY_PNG))
                AUDIO_PATH ->
                    MockResponse()
                        .setHeader("Content-Type", "audio/wav")
                        .setBody(Buffer().write(buildSilentWav()))
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun feedXml(): String {
        val audioUrl = server.url(AUDIO_PATH).toString()
        val coverUrl = server.url(COVER_PATH).toString()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>$TEST_PODCAST_TITLE</title>
                <description>Integration test feed</description>
                <link>https://example.test/podcast</link>
                <image>
                  <url>$coverUrl</url>
                </image>
                <item>
                  <title>$TEST_EPISODE_TITLE</title>
                  <description>Episode for end to end testing.</description>
                  <link>https://example.test/podcast/episode-1</link>
                  <guid>$TEST_EPISODE_GUID</guid>
                  <pubDate>Wed, 04 Mar 2026 10:00:00 GMT</pubDate>
                  <itunes:duration>00:00:02</itunes:duration>
                  <enclosure url="$audioUrl" type="audio/wav" length="${SILENT_WAV.size}" />
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

    private fun buildSilentWav(): ByteArray {
        return SILENT_WAV
    }

    private companion object {
        const val FEED_PATH = "/feed.xml"
        const val COVER_PATH = "/cover.png"
        const val AUDIO_PATH = "/episode.wav"
        const val TEST_PODCAST_TITLE = "Integration Test Podcast"
        const val TEST_EPISODE_TITLE = "Integration Episode 1"
        const val TEST_EPISODE_GUID = "integration-episode-1"

        const val NAV_SETTINGS = "Settings"
        const val NAV_HOME = "Home"
        const val NAV_DOWNLOADS = "Downloads"
        const val SETTINGS_TITLE = "Settings"
        const val HOME_TITLE = "My Podcasts"
        const val HOME_EMPTY_TEXT = "No podcasts yet. Press +"
        const val ADD_BUTTON = "Add"
        const val ADD_SUCCESS_TEXT = "Podcast added successfully"
        const val TOGGLE_FAVORITE = "Favorit markieren/entfernen"
        const val TOGGLE_PLAYED = "Als gespielt/ungespielt markieren"
        const val PLAY_PAUSE = "Play/Pause"
        const val MINI_PLAYER_PLAY = "Play"
        const val MINI_PLAYER_PAUSE = "Pause"
        const val DOWNLOAD_EPISODE = "Episode herunterladen"
        const val EPISODE_DOWNLOADED = "Episode ist heruntergeladen"
        const val NO_DOWNLOADS_TEXT = "No downloads available"
        const val FAVORITES = "Favorites"
        const val HISTORY = "History"
        const val BACK = "Back"

        val SILENT_WAV: ByteArray = createSilentWav(seconds = 2)
        val TINY_PNG: ByteArray =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNoaGgAAAMEAYFL09IQAAAAAElFTkSuQmCC"
            )

        private fun createSilentWav(seconds: Int): ByteArray {
            val sampleRate = 44_100
            val bitsPerSample = 16
            val channels = 1
            val bytesPerSample = bitsPerSample / 8
            val dataSize = sampleRate * seconds * channels * bytesPerSample
            val byteRate = sampleRate * channels * bytesPerSample
            val totalSize = 44 + dataSize
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(totalSize - 8)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort((channels * bytesPerSample).toShort())
            buffer.putShort(bitsPerSample.toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)

            repeat(dataSize / bytesPerSample) {
                buffer.putShort(0)
            }

            return buffer.array()
        }
    }
}
