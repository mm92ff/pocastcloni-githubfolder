package com.example.pocastcloni.data.repository

import android.content.Context
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.di.DispatcherProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastCommandAdapterTest {
    private val dispatcher = StandardTestDispatcher()
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private val dispatcherProvider =
        mockk<DispatcherProvider>().also {
            every { it.io } returns dispatcher
        }
    private val adapter =
        PodcastCommandAdapter(
            podcastDao = podcastDao,
            dispatcherProvider = dispatcherProvider,
            context = mockk<Context>(relaxed = true)
        )

    @Test
    fun `mark all as seen only clears podcast badges`() = runTest(dispatcher) {
        adapter.markAllAsSeen()

        coVerify(exactly = 1) { podcastDao.markAllAsSeen() }
    }

    @Test
    fun `mark episode played delegates atomic badge reconciliation`() = runTest(dispatcher) {
        val playedAt = Date(1_000L)

        adapter.markEpisodePlayed(episodeId = 42L, played = true, datePlayed = playedAt)

        coVerify(exactly = 1) {
            podcastDao.markEpisodePlayedAndReconcileBadge(
                episodeId = 42L,
                isPlayed = true,
                datePlayed = playedAt
            )
        }
    }

    @Test
    fun `toggle episode delegates atomic badge reconciliation`() = runTest(dispatcher) {
        val episode =
            mockk<com.example.pocastcloni.domain.model.Episode> {
                every { episodeId } returns 43L
            }

        adapter.toggleEpisodePlayed(episode)

        coVerify(exactly = 1) {
            podcastDao.toggleEpisodePlayedAndReconcileBadge(
                episodeId = 43L,
                datePlayed = any()
            )
        }
    }

    @Test
    fun `reorder maps each rss url once to contiguous sort indices`() = runTest(dispatcher) {
        val updates = slot<List<PodcastSortUpdate>>()
        val rssUrlsInOrder = listOf("feed-c", "feed-a", "feed-b")

        adapter.reorderPodcasts(rssUrlsInOrder = rssUrlsInOrder)

        coVerify(exactly = 1) { podcastDao.updatePodcastSortOrders(capture(updates)) }
        assertEquals(
            listOf(
                PodcastSortUpdate("feed-c", 0L),
                PodcastSortUpdate("feed-a", 1L),
                PodcastSortUpdate("feed-b", 2L)
            ),
            updates.captured
        )
    }
}
