package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MarkEpisodePlayedUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: MarkEpisodePlayedUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val feedUrl = "https://example.com/feed.rss"

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        useCase = MarkEpisodePlayedUseCase(repository, dispatcherProvider)
    }

    private fun episode(guid: String, isPlayed: Boolean = true) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = feedUrl,
        title = guid,
        description = "",
        pubDate = Date(),
        link = "",
        enclosureUrl = "https://example.com/$guid.mp3",
        isPlayed = isPlayed
    )

    private fun podcast(hasNewEpisodes: Boolean) = PodcastEntity(
        rssUrl = feedUrl,
        title = "Podcast",
        description = "",
        imageUrl = "https://img.jpg",
        hasNewEpisodes = hasNewEpisodes
    )

    @Test
    fun `marks episode as played in repository`() = runTest(testDispatcher) {
        val ep = episode("ep-1")
        coEvery { repository.getEpisode("ep-1") } returns ep
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(ep)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcast(hasNewEpisodes = false)

        useCase("ep-1")

        coVerify { repository.markEpisodePlayed("ep-1", true, any()) }
    }

    @Test
    fun `clears hasNewEpisodes dot when played episode is the latest`() = runTest(testDispatcher) {
        val latestEp = episode("ep-latest", isPlayed = true)
        coEvery { repository.getEpisode("ep-latest") } returns latestEp
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(latestEp)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcast(hasNewEpisodes = true)

        useCase("ep-latest")

        val slot = slot<PodcastEntity>()
        coVerify { repository.updatePodcastEntity(capture(slot)) }
        assertFalse("Dot should be cleared when latest episode is played", slot.captured.hasNewEpisodes)
    }

    @Test
    fun `keeps hasNewEpisodes dot when an unplayed episode still exists`() = runTest(testDispatcher) {
        val playedEp = episode("ep-old", isPlayed = true)
        val unplayedLatest = episode("ep-latest", isPlayed = false)
        coEvery { repository.getEpisode("ep-old") } returns playedEp
        // getEpisodesForSync returns sorted by date desc: latest (unplayed) is first
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(unplayedLatest, playedEp)
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcast(hasNewEpisodes = true)

        useCase("ep-old")

        // The dot should remain because the latest episode is still unplayed
        coVerify(exactly = 0) { repository.updatePodcastEntity(any()) }
    }

    @Test
    fun `does not crash when episode not found after marking`() = runTest(testDispatcher) {
        coEvery { repository.getEpisode("missing-guid") } returns null

        // Should not throw
        useCase("missing-guid")
    }

    @Test
    fun `does not update podcast when hasNewEpisodes already matches desired state`() = runTest(testDispatcher) {
        val latestEp = episode("ep-1", isPlayed = true)
        coEvery { repository.getEpisode("ep-1") } returns latestEp
        coEvery { repository.getEpisodesForSync(feedUrl) } returns listOf(latestEp)
        // Podcast already has hasNewEpisodes=false — no change needed
        coEvery { repository.getPodcastEntityByUrl(feedUrl) } returns podcast(hasNewEpisodes = false)

        useCase("ep-1")

        coVerify(exactly = 0) { repository.updatePodcastEntity(any()) }
    }
}
