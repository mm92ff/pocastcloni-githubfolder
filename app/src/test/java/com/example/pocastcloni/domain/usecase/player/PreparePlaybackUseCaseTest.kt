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

    private val testDispatcher = StandardTestDispatcher()
    private val feedUrl = "https://example.com/feed.rss"
    private val streamUrl = "https://example.com/episode.mp3"

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        useCase = PreparePlaybackUseCase(repository, dispatcherProvider)
        coEvery { repository.getPodcastEntityByUrl(any()) } returns null
    }

    private fun episode(
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
        playbackPositionMs = positionMs
    )

    @Test
    fun `not downloaded episode streams via enclosureUrl`() = runTest(testDispatcher) {
        val ep = episode(downloadStatus = DownloadStatus.NOT_DOWNLOADED)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertEquals(streamUrl, result.playUri)
        assertEquals(ep, result.episode)
    }

    @Test
    fun `downloaded episode with valid local file plays from file URI`() = runTest(testDispatcher) {
        val file = tempFolder.newFile("episode.mp3")
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = file.absolutePath)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertTrue("Should play from local file URI", result.playUri.startsWith("file:"))
    }

    @Test
    fun `downloaded episode with content URI plays MediaStore URI directly`() = runTest(testDispatcher) {
        val contentUri = "content://media/external/downloads/12345"
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = contentUri)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertEquals(contentUri, result.playUri)
    }

    @Test
    fun `downloaded episode with missing file falls back to stream and resets status`() = runTest(testDispatcher) {
        val missingPath = "/nonexistent/path/episode.mp3"
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = missingPath)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertEquals("Should fall back to stream URL", streamUrl, result.playUri)
        coVerify { repository.updateDownloadStatus("guid-1", DownloadStatus.NOT_DOWNLOADED, null) }
    }

    @Test
    fun `downloaded episode with null downloadPath falls back to stream and resets status`() = runTest(testDispatcher) {
        val ep = episode(downloadStatus = DownloadStatus.DOWNLOADED, downloadPath = null)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertEquals("Should fall back to stream URL", streamUrl, result.playUri)
        coVerify { repository.updateDownloadStatus("guid-1", DownloadStatus.NOT_DOWNLOADED, null) }
    }

    @Test
    fun `playback starts from saved position`() = runTest(testDispatcher) {
        val ep = episode(positionMs = 42_000L)
        coEvery { repository.getEpisode("guid-1") } returns ep

        val result = useCase("guid-1")

        assertEquals(42_000L, result.startPosition)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws IllegalStateException when episode not found`() = runTest(testDispatcher) {
        coEvery { repository.getEpisode("unknown-guid") } returns null

        useCase("unknown-guid")
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
        coEvery { repository.getEpisode("guid-1") } returns ep
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcastEntity

        val result = useCase("guid-1")

        assertEquals("My Podcast", result.podcast?.title)
    }
}
