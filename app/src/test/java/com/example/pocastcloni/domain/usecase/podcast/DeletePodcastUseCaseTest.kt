package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastRemovalGateway
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeletePodcastUseCaseTest {
    private val feedSyncStore = mockk<FeedSyncStore>()
    private val podcastCommands = mockk<PodcastCommandPort>(relaxed = true)
    private val removalGateway = mockk<PodcastRemovalGateway>(relaxed = true)
    private val useCase = DeletePodcastUseCase(feedSyncStore, podcastCommands, removalGateway)

    @Test
    fun `deletion removes cover only after database and episode cleanup`() = runTest {
        val podcast = Podcast(FEED_URL, "Podcast", "", COVER_URL)
        val episodes = listOf(episode())
        coEvery { feedSyncStore.getEpisodesForSync(FEED_URL) } returns episodes

        useCase(podcast)

        coVerifyOrder {
            feedSyncStore.getEpisodesForSync(FEED_URL)
            removalGateway.cancelActiveDownloads(episodes)
            podcastCommands.deletePodcast(podcast)
            removalGateway.deleteDownloadedFiles(episodes)
            removalGateway.deletePodcastCover(FEED_URL)
        }
    }

    private fun episode() =
        Episode(
            guid = "episode-guid",
            podcastRssUrl = FEED_URL,
            title = "Episode",
            description = "Description",
            pubDate = null,
            link = "https://example.com/episode",
            enclosureUrl = "https://example.com/episode.mp3"
        )

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val COVER_URL = "https://example.com/cover.png"
    }
}
