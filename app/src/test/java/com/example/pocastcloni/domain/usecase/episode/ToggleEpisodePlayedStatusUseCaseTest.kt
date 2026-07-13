package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToggleEpisodePlayedStatusUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PodcastRepository
    private lateinit var useCase: ToggleEpisodePlayedStatusUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        val dispatcherProvider = mockk<DispatcherProvider>()
        every { dispatcherProvider.io } returns testDispatcher
        useCase = ToggleEpisodePlayedStatusUseCase(repository, dispatcherProvider)
    }

    @Test
    fun `toggles episode without reading stale podcast snapshots`() = runTest(testDispatcher) {
        val episode = EpisodeEntity(
            episodeId = 101L,
            guid = "episode",
            podcastRssUrl = "https://example.com/feed.xml",
            title = "Episode",
            description = "",
            pubDate = null,
            link = "",
            enclosureUrl = "https://example.com/episode.mp3"
        )
        coEvery { repository.getEpisode(episode.episodeId) } returns episode

        useCase(episode.episodeId)

        coVerify(exactly = 1) { repository.toggleEpisodePlayed(episode) }
        coVerify(exactly = 0) { repository.getEpisodesForSync(any()) }
        coVerify(exactly = 0) { repository.getPodcastEntityByUrl(any()) }
    }
}
