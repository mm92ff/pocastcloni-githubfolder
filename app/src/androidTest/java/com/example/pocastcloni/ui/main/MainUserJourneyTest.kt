package com.example.pocastcloni.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.repository.UserPreferencesRepositoryImpl
import com.example.pocastcloni.data.repository.InstallationStateProvider
import com.example.pocastcloni.data.repository.InstallationState
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.service.PodcastPlaybackService
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainUserJourneyTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        runBlocking { resetAppState() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { resetAppState() }
    }

    @Test
    fun seededPodcast_drivesCoreUserJourney() {
        runBlocking { seedPodcast() }
        scenario =
            ActivityScenario.launch(MainActivity::class.java).also {
                it.moveToState(Lifecycle.State.RESUMED)
            }

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

        markEpisodeDownloaded()
        waitUntil(timeoutMillis = 20_000) { hasNodeWithContentDescription(EPISODE_DOWNLOADED) }
        waitForDownloadStatus(TEST_EPISODE_GUID, DownloadStatus.DOWNLOADED)

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

        UserPreferencesRepositoryImpl(
            context = context,
            installationStateProvider = InstallationStateProvider { InstallationState.FRESH }
        ).restoreSettings(
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

    private suspend fun seedPodcast() {
        val dao = AppDatabase.getDatabase(context).podcastDao()
        dao.insertPodcast(
            PodcastEntity(
                rssUrl = TEST_FEED_URL,
                title = TEST_PODCAST_TITLE,
                description = "Integration test feed",
                imageUrl = "https://example.test/cover.png"
            )
        )
        dao.insertEpisode(
            EpisodeEntity(
                guid = TEST_EPISODE_GUID,
                podcastRssUrl = TEST_FEED_URL,
                title = TEST_EPISODE_TITLE,
                description = "Episode for end to end testing.",
                pubDate = Date(1_772_618_400_000),
                link = "https://example.test/podcast/episode-1",
                enclosureUrl = "https://example.test/episode.wav",
                type = "audio/wav",
                fileSize = SILENT_WAV.size.toLong(),
                duration = 2_000
            )
        )
    }

    private fun markEpisodeDownloaded() = runBlocking {
        val directory = File(context.filesDir, Constants.DOWNLOADS_DIR).apply { mkdirs() }
        val audioFile = File(directory, "$TEST_EPISODE_GUID.wav").apply { writeBytes(SILENT_WAV) }
        val episode =
            requireNotNull(
                AppDatabase.getDatabase(context).podcastDao().getEpisodeByFeedAndGuid(
                    podcastRssUrl = TEST_FEED_URL,
                    guid = TEST_EPISODE_GUID
                )
            )
        require(episode.episodeId > 0L)
        AppDatabase.getDatabase(context).podcastDao().updateDownloadStatus(
            episodeId = episode.episodeId,
            status = DownloadStatus.DOWNLOADED,
            path = audioFile.absolutePath
        )
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
            val episode =
                runBlocking {
                    AppDatabase.getDatabase(context).podcastDao().getEpisodeByFeedAndGuid(
                        podcastRssUrl = TEST_FEED_URL,
                        guid = TEST_EPISODE_GUID
                    )
                }
            episode != null && predicate(episode)
        }
    }

    private fun waitForDownloadStatus(
        guid: String,
        expected: DownloadStatus,
        timeoutMillis: Long = 20_000
    ) {
        waitUntil(timeoutMillis) {
            val episode =
                runBlocking {
                    AppDatabase.getDatabase(context).podcastDao().getEpisodeByFeedAndGuid(
                        podcastRssUrl = TEST_FEED_URL,
                        guid = guid
                    )
                }
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

    private companion object {
        const val TEST_FEED_URL = "https://example.test/feed.xml"
        const val TEST_PODCAST_TITLE = "Integration Test Podcast"
        const val TEST_EPISODE_TITLE = "Integration Episode 1"
        const val TEST_EPISODE_GUID = "integration-episode-1"

        const val NAV_HOME = "Home"
        const val NAV_DOWNLOADS = "Downloads"
        const val HOME_TITLE = "My Podcasts"
        const val TOGGLE_FAVORITE = "Mark/unmark as favorite"
        const val TOGGLE_PLAYED = "Mark as played/unplayed"
        const val EPISODE_DOWNLOADED = "Episode is downloaded"
        const val NO_DOWNLOADS_TEXT = "No downloads available"
        const val FAVORITES = "Favorites"
        const val HISTORY = "History"
        const val BACK = "Back"

        val SILENT_WAV: ByteArray = createSilentWav(seconds = 2)
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
