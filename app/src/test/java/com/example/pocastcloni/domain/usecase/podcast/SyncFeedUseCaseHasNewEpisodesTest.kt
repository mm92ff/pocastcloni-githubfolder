package com.example.pocastcloni.domain.usecase.podcast

import android.util.Xml
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.FeedSyncPersistence
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
import java.net.HttpURLConnection
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
    private lateinit var feedSyncPersistence: FeedSyncPersistence
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
        feedSyncPersistence = mockk(relaxed = true)
        downloadEpisodeUseCase = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher

        useCase = SyncFeedUseCase(
            podcastService = podcastService,
            localPodcastService = localPodcastService,
            podcastRepositoryProvider = Provider { repository },
            feedSyncPersistence = feedSyncPersistence,
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

    private fun mockSuccessResponse(
        guid: String,
        headers: Headers = Headers.headersOf()
    ): Response<okhttp3.ResponseBody> {
        val body = makeRssBody(guid)
        return Response.success(body, headers)
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
    fun `full sync delegates duplicate episode without pre-reading badge state`() = runTest(testDispatcher) {
        val guid = "episode-guid-1"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns guid

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<List<EpisodeEntity>>()
        coVerify { feedSyncPersistence.persistFeedUpdate(any(), null, capture(slot)) }
        assertTrue(slot.captured.single().guid == guid)
        coVerify(exactly = 0) { repository.getEpisodesForSync(any()) }
    }

    @Test
    fun `full sync delegates new episode to transactional persistence`() = runTest(testDispatcher) {
        val newGuid = "new-episode-guid"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(newGuid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns null

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<List<EpisodeEntity>>()
        coVerify { feedSyncPersistence.persistFeedUpdate(any(), null, capture(slot)) }
        assertTrue(slot.captured.single().guid == newGuid)
    }

    @Test
    fun `full sync never copies existing badge state into feed update`() = runTest(testDispatcher) {
        val guid = "episode-guid-1"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existingPodcast(hasNew = true)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns guid

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        coVerify { feedSyncPersistence.persistFeedUpdate(any(), null, any()) }
        coVerify(exactly = 0) { repository.getEpisodesForSync(any()) }
    }

    @Test
    fun `full sync inserts new podcast entity when podcast does not exist yet`() = runTest(testDispatcher) {
        val guid = "brand-new-guid"
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns null
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns null
        coEvery { repository.getMaxSortOrder() } returns 5L

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        val slot = slot<PodcastEntity>()
        coVerify { feedSyncPersistence.persistFeedUpdate(any(), capture(slot), any()) }
        assertFalse("Room must derive the new badge from insert results", slot.captured.hasNewEpisodes)
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
        coVerify(exactly = 0) { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) }
        coVerify(exactly = 0) { feedSyncPersistence.touchLastRefreshed(any(), any()) }
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
        coVerify(exactly = 0) { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) }
        coVerify(exactly = 0) { feedSyncPersistence.touchLastRefreshed(any(), any()) }
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
            feedSyncPersistence.persistFeedUpdate(
                any(),
                null,
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

        coVerify {
            feedSyncPersistence.persistFeedUpdate(any(), null, match { episodes -> episodes.isEmpty() })
        }
    }

    @Test
    fun `not modified response updates only last refreshed`() = runTest(testDispatcher) {
        val response = mockk<Response<ResponseBody>>()
        val downloadable = existingEpisode("known-guid").copy(episodeId = 44L)
        every { response.code() } returns HttpURLConnection.HTTP_NOT_MODIFIED
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns response
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns
            existingPodcast().copy(autoDownloadEnabled = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns "known-guid"
        coEvery { feedSyncPersistence.touchLastRefreshed(feedUrl, any()) } returns true
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(downloadable)

        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        coVerifyOrder {
            feedSyncPersistence.touchLastRefreshed(feedUrl, any())
            downloadEpisodeUseCase(downloadable.episodeId)
        }
        coVerify(exactly = 0) { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) }
    }

    @Test
    fun `failed persistence does not approve feed or start downloads`() = runTest(testDispatcher) {
        val localUrl = "http://10.0.2.2/feed.rss"
        val registry = LocalNetworkAccessRegistry()
        val localUseCase = SyncFeedUseCase(
            podcastService = podcastService,
            localPodcastService = localPodcastService,
            podcastRepositoryProvider = Provider { repository },
            feedSyncPersistence = feedSyncPersistence,
            downloadEpisodeUseCase = downloadEpisodeUseCase,
            dispatcherProvider = dispatcherProvider,
            localNetworkAccessRegistry = registry
        )
        val existing = existingPodcast().copy(
            rssUrl = localUrl,
            allowInsecureHttp = true,
            allowLocalNetwork = true,
            autoDownloadEnabled = true
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
        coEvery { repository.getPodcastEntityByUrl(localUrl) } returns existing
        coEvery { repository.getLatestEpisodeGuid(localUrl) } returns null
        coEvery { repository.getEpisodesForSync(localUrl) } returns emptyList()
        coEvery { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) } throws
            IllegalStateException("injected failure")

        assertTrue(
            runCatching {
                localUseCase(localUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)
            }.isFailure
        )

        assertFalse(registry.isApproved(localUrl))
        coVerify(exactly = 0) { downloadEpisodeUseCase(any()) }
    }

    @Test
    fun `concurrent auto download enablement returned by commit starts downloads`() = runTest(testDispatcher) {
        val guid = "new-guid"
        val downloadable = existingEpisode(guid).copy(episodeId = 55L)
        val persistenceEntered = CompletableDeferred<Unit>()
        val releasePersistence = CompletableDeferred<Boolean>()
        coEvery { podcastService.fetchRawFeed(feedUrl, any(), any()) } returns mockSuccessResponse(guid)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns
            existingPodcast().copy(autoDownloadEnabled = false)
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns "old-guid"
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(downloadable)
        coEvery { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) } coAnswers {
            persistenceEntered.complete(Unit)
            releasePersistence.await()
        }

        val sync = async {
            useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)
        }
        runCurrent()
        persistenceEntered.await()
        releasePersistence.complete(true)
        runCurrent()
        sync.await()

        coVerifyOrder {
            feedSyncPersistence.persistFeedUpdate(any(), null, any())
            downloadEpisodeUseCase(downloadable.episodeId)
        }
    }

    @Test
    fun `rolled back headers are reused by the next request`() = runTest(testDispatcher) {
        val existing = existingPodcast().copy(
            lastModifiedHeader = "old-last-modified",
            eTagHeader = "old-etag"
        )
        val notModified = mockk<Response<ResponseBody>>()
        every { notModified.code() } returns HttpURLConnection.HTTP_NOT_MODIFIED
        coEvery {
            podcastService.fetchRawFeed(feedUrl, "old-last-modified", "old-etag")
        } returnsMany listOf(
            mockSuccessResponse(
                guid = "new-guid",
                headers = Headers.headersOf(
                    Constants.Network.HEADER_LAST_MODIFIED,
                    "new-last-modified",
                    Constants.Network.HEADER_ETAG,
                    "new-etag"
                )
            ),
            notModified
        )
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns existing
        coEvery { repository.getLatestEpisodeGuid(feedUrl) } returns "known-guid"
        coEvery { feedSyncPersistence.persistFeedUpdate(any(), any(), any()) } throws
            IllegalStateException("injected transaction failure")

        assertTrue(
            runCatching {
                useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)
            }.isFailure
        )
        useCase(feedUrl, downloadLimit = 3, mode = FeedUpdateMode.ALWAYS_FULL)

        coVerify(exactly = 2) {
            podcastService.fetchRawFeed(feedUrl, "old-last-modified", "old-etag")
        }
        coVerify(exactly = 1) { feedSyncPersistence.touchLastRefreshed(feedUrl, any()) }
    }
}
