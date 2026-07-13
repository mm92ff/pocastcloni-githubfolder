package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import org.junit.rules.TemporaryFolder
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class PreparePlaybackUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: PodcastRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: PreparePlaybackUseCase
    private lateinit var localNetworkAccessRegistry: LocalNetworkAccessRegistry

    private val testDispatcher = StandardTestDispatcher()
    private val feedUrl = "https://example.com/feed.rss"
    private val streamUrl = "https://example.com/episode.mp3"

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        localNetworkAccessRegistry = LocalNetworkAccessRegistry()
        useCase = PreparePlaybackUseCase(
            repository,
            dispatcherProvider,
            localNetworkAccessRegistry
        )
        coEvery { repository.getPodcastEntityByUrl(any()) } returns null
    }

    private fun episode(
        episodeId: Long = EPISODE_ID,
        guid: String = "guid-1",
        downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        downloadPath: String? = null,
        positionMs: Long = 0L
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = feedUrl,
        title = "Title",
        description = "",
        pubDate = Date(),
        link = "",
        enclosureUrl = streamUrl,
        downloadStatus = downloadStatus,
        downloadPath = downloadPath,
        playbackPositionMs = positionMs,
        episodeId = episodeId
    )

    @Test
    fun `not downloaded episode streams via enclosureUrl`() = runTest(testDispatcher) {
        val ep = episode(downloadStatus = DownloadStatus.NOT_DOWNLOADED)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertEquals(streamUrl, result.playUri)
        assertEquals(ep, result.episode)
    }

    @Test
    fun `downloaded episode with valid local file plays from file URI`() = runTest(testDispatcher) {
        val file = tempFolder.newFile("episode.mp3")
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = file.absolutePath)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertTrue("Should play from local file URI", result.playUri.startsWith("file:"))
    }

    @Test
    fun `downloaded episode with content URI plays MediaStore URI directly`() = runTest(testDispatcher) {
        val contentUri = "content://media/external/downloads/12345"
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = contentUri)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertEquals(contentUri, result.playUri)
    }

    @Test
    fun `downloaded episode with missing file falls back to stream and resets status`() = runTest(testDispatcher) {
        val missingPath = "/nonexistent/path/episode.mp3"
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = missingPath)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertEquals("Should fall back to stream URL", streamUrl, result.playUri)
        coVerify { repository.updateDownloadStatus(EPISODE_ID, DownloadStatus.NOT_DOWNLOADED, null) }
    }

    @Test
    fun `downloaded episode with null downloadPath falls back to stream and resets status`() = runTest(testDispatcher) {
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = null)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertEquals("Should fall back to stream URL", streamUrl, result.playUri)
        coVerify { repository.updateDownloadStatus(EPISODE_ID, DownloadStatus.NOT_DOWNLOADED, null) }
    }

    @Test
    fun `playback starts from saved position`() = runTest(testDispatcher) {
        val ep = episode(positionMs = 42_000L)
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep

        val result = useCase(EPISODE_ID)

        assertEquals(42_000L, result.startPosition)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws IllegalStateException when episode not found`() = runTest(testDispatcher) {
        coEvery { repository.getEpisode(UNKNOWN_EPISODE_ID) } returns null

        useCase(UNKNOWN_EPISODE_ID)
    }

    @Test
    fun `loads podcast info alongside episode`() = runTest(testDispatcher) {
        val ep = episode()
        val podcastEntity = PodcastEntity(
            rssUrl = feedUrl,
            title = "My Podcast",
            description = "",
            imageUrl = "https://img.jpg"
        )
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcastEntity

        val result = useCase(EPISODE_ID)

        assertEquals("My Podcast", result.podcast?.title)
    }

    @Test
    fun `approved same-origin local episode can stream`() = runTest(testDispatcher) {
        val localFeed = "http://192.168.1.20:8080/feed.xml"
        val localAudio = "http://192.168.1.20:8080/audio.mp3"
        val ep = episode().copy(podcastRssUrl = localFeed, enclosureUrl = localAudio)
        val podcast = PodcastEntity(
            rssUrl = localFeed,
            title = "Local",
            description = "",
            imageUrl = "http://192.168.1.20:8080/cover.jpg",
            allowInsecureHttp = true,
            allowLocalNetwork = true
        )
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep
        coEvery { repository.getPodcastEntityByUrl(localFeed) } returns podcast

        val result = useCase(EPISODE_ID)

        assertEquals(localAudio, result.playUri)
        assertTrue(localNetworkAccessRegistry.isApproved(localAudio))
    }

    @Test
    fun `approved local feed cannot stream from another private origin`() = runTest(testDispatcher) {
        val localFeed = "http://192.168.1.20:8080/feed.xml"
        val foreignAudio = "http://192.168.1.21:8080/audio.mp3"
        val ep = episode().copy(podcastRssUrl = localFeed, enclosureUrl = foreignAudio)
        val podcast = PodcastEntity(
            rssUrl = localFeed,
            title = "Local",
            description = "",
            imageUrl = "",
            allowInsecureHttp = true,
            allowLocalNetwork = true
        )
        coEvery { repository.getEpisode(EPISODE_ID) } returns ep
        coEvery { repository.getPodcastEntityByUrl(localFeed) } returns podcast

        assertTrue(runCatching { useCase(EPISODE_ID) }.isFailure)
    }

    private companion object {
        const val EPISODE_ID = 101L
        const val UNKNOWN_EPISODE_ID = 999L
    }
}
