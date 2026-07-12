package com.example.pocastcloni.domain.usecase.podcast

import android.util.Xml
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import com.example.pocastcloni.util.Constants
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.Response
import java.util.Date
import javax.inject.Provider

/**
 * Regression tests for the hasNewEpisodes flag bug:
 * a full sync should only mark hasNewEpisodes=true when episodes that are
 * genuinely new (not already in the DB) are inserted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncFeedUseCaseHasNewEpisodesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var podcastService: PodcastService
    private lateinit var localPodcastService: PodcastService
    private lateinit var repository: PodcastRepository
    private lateinit var downloadEpisodeUseCase: DownloadEpisodeUseCase
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: SyncFeedUseCase

    private val feedUrl = "https://example.com/feed.rss"
    private val testDispatcher = StandardTestDispatcher()

    @After
    fun teardown() {
        unmockkAll()
    }

    @Before
    fun setup() {
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } returns XmlPullParserFactory.newInstance().newPullParser()

        podcastService = mockk()
        localPodcastService = mockk()
        repository = mockk(relaxed = true)
        downloadEpisodeUseCase = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher

        useCase = SyncFeedUseCase(
            podcastService = podcastService,
            localPodcastService = localPodcastService,
            podcastRepositoryProvider = Provider { repository },
            downloadEpisodeUseCase = downloadEpisodeUseCase,
            dispatcherProvider = dispatcherProvider,
            localNetworkAccessRegistry = LocalNetworkAccessRegistry()
        )
    }

    private fun makeRssBody(
        guid: String,
        episodeTitle: String = "Episode Title",
        imageUrl: String = "https://example.com/cover.jpg",
        enclosureUrl: String = "https://example.com/$guid.mp3"
    ): okhttp3.ResponseBody {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
  <channel>
    <title>Test Podcast</title>
    <itunes:image href="$imageUrl"/>
    <item>
      <title>$episodeTitle</title>
      <guid isPermaLink="false">$guid</guid>
      <enclosure url="$enclosureUrl" type="audio/mpeg" length="0"/>
    </item>
  </channel>
</rss>"""
        return xml.toResponseBody("application/rss+xml".toMediaType())
    }

    private fun mockSuccessResponse(guid: String): Response<okhttp3.ResponseBody> {
        val body = makeRssBody(guid)
        return Response.success(body, Headers.headersOf())
    }

    private fun existingPodcast(hasNew: Boolean = false) = PodcastEntity(
        rssUrl = feedUrl,
        title = "Test Podcast",
        description = "",
        imageUrl = "https://example.com/cover.jpg",
        hasNewEpisodes = hasNew
    )

    private fun existingEpisode(guid: String) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = feedUrl,
        title = "Episode Title",
        description = "",
        pubDate = Date(),
        link = "",
        enclosureUrl = "https://example.com/$guid.mp3"
    )

    @Test
    fun `full sync does not set hasNewEpisodes when all feed episodes already in DB`() = runTest(testDispatcher) {
        val guid = "episode-guid-1"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns guid
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(existingEpisode(guid))
        coEvery { repository.getMaxSortOrder() } returns 0L

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<PodcastEntity>()
        coVerify { repository.updatePodcastEntity(capture(slot)) }
        assertFalse("hasNewEpisodes should stay false when no new episodes", slot.captured.hasNewEpisodes)
    }

    @Test
    fun `full sync sets hasNewEpisodes when feed contains episode not in DB`() = runTest(testDispatcher) {
        val newGuid = "new-episode-guid"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(newGuid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns null
        coEvery { repository.getEpisodesForSync(feedUrl) } returns emptyList() // No existing episodes
        coEvery { repository.getMaxSortOrder() } returns 0L

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<PodcastEntity>()
        coVerify { repository.updatePodcastEntity(capture(slot)) }
        assertTrue("hasNewEpisodes should be true when new episode arrives", slot.captured.hasNewEpisodes)
    }

    @Test
    fun `full sync preserves existing hasNewEpisodes=true when no new episodes`() = runTest(testDispatcher) {
        val guid = "episode-guid-1"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = true)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns guid
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(existingEpisode(guid))
        coEvery { repository.getMaxSortOrder() } returns 0L

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<PodcastEntity>()
        coVerify { repository.updatePodcastEntity(capture(slot)) }
        assertTrue("existing hasNewEpisodes=true should be preserved", slot.captured.hasNewEpisodes)
    }

    @Test
    fun `full sync inserts new podcast entity when podcast does not exist yet`() = runTest(testDispatcher) {
        val guid = "brand-new-guid"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns null
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns null
        coEvery { repository.getEpisodesForSync(feedUrl) } returns emptyList()
        coEvery { repository.getMaxSortOrder() } returns 5L

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<PodcastEntity>()
        coVerify { repository.insertPodcastEntity(capture(slot)) }
        assertTrue("New podcast should have hasNewEpisodes=true", slot.captured.hasNewEpisodes)
    }

    @Test
    fun `oversized declared feed does not write partial data or start downloads`() = runTest(testDispatcher) {
        val body = object : ResponseBody() {
            override fun contentType() = "application/rss+xml".toMediaType()
            override fun contentLength() = Constants.SecurityLimits.MAX_FEED_BYTES + 1
            override fun source(): BufferedSource = Buffer()
        }
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns
            Response.success(body, Headers.headersOf())
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns null

        assertTrue(
            runCatching {
                useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)
            }.isFailure
        )
        coVerify(exactly = 0) { repository.insertPodcastEntity(any()) }
        coVerify(exactly = 0) { repository.updatePodcastEntity(any()) }
        coVerify(exactly = 0) { repository.insertEpisodes(any()) }
        coVerify(exactly = 0) { downloadEpisodeUseCase(any()) }
    }

    @Test
    fun `oversized episode metadata does not write partial data or start downloads`() = runTest(testDispatcher) {
        val longTitle = "x".repeat(Constants.SecurityLimits.MAX_TITLE_CHARS + 1)
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns
            Response.success(makeRssBody("oversized", longTitle), Headers.headersOf())
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns null
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns null
        coEvery { repository.getEpisodesForSync(feedUrl) } returns emptyList()

        assertTrue(
            runCatching {
                useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)
            }.isFailure
        )
        coVerify(exactly = 0) { repository.insertPodcastEntity(any()) }
        coVerify(exactly = 0) { repository.updatePodcastEntity(any()) }
        coVerify(exactly = 0) { repository.insertEpisodes(any()) }
        coVerify(exactly = 0) { downloadEpisodeUseCase(any()) }
    }

    @Test
    fun `approved local feed uses only the local network service`() = runTest(testDispatcher) {
        val localUrl = "http://10.0.2.2/feed.rss"
        val existingLocalPodcast = existingPodcast().copy(
            rssUrl = localUrl,
            allowInsecureHttp = true,
            allowLocalNetwork = true
        )
        coEvery {
            localPodcastService.fetchRawFeed(localUrl, any(), any())
        } returns Response.success(
            makeRssBody(
                guid = "local-guid",
                imageUrl = "http://10.0.2.2/cover.jpg",
                enclosureUrl = "http://10.0.2.2/local-guid.mp3"
            ),
            Headers.headersOf()
        )
        coEvery { repository.getPodcastEntityByUrl(localUrl) } returns existingLocalPodcast
        coEvery { repository.getLatestEpisodeGuid(localUrl) } returns null
        coEvery { repository.getEpisodesForSync(localUrl) } returns emptyList()

        useCase(localUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        coVerify(exactly = 1) { localPodcastService.fetchRawFeed(localUrl, any(), any()) }
        coVerify(exactly = 0) { podcastService.fetchRawFeed(localUrl, any(), any()) }
        coVerify {
            repository.insertEpisodes(
                match { episodes -> episodes.single().enclosureUrl == "http://10.0.2.2/local-guid.mp3" }
            )
        }
    }

    @Test
    fun `local feed drops an enclosure from another private host`() = runTest(testDispatcher) {
        val localUrl = "http://10.0.2.2/feed.rss"
        val existingLocalPodcast = existingPodcast().copy(
            rssUrl = localUrl,
            allowInsecureHttp = true,
            allowLocalNetwork = true
        )
        coEvery {
            localPodcastService.fetchRawFeed(localUrl, any(), any())
        } returns Response.success(
            makeRssBody(
                guid = "foreign-guid",
                imageUrl = "http://10.0.2.2/cover.jpg",
                enclosureUrl = "http://192.168.1.30/audio.mp3"
            ),
            Headers.headersOf()
        )
        coEvery { repository.getPodcastEntityByUrl(localUrl) } returns existingLocalPodcast
        coEvery { repository.getLatestEpisodeGuid(localUrl) } returns null
        coEvery { repository.getEpisodesForSync(localUrl) } returns emptyList()

        useCase(localUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        coVerify(exactly = 0) { repository.insertEpisodes(any()) }
    }
}
