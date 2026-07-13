package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshCoordinator
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FeedUpdateRunnerTest {
    private val coordinator = mockk<FeedRefreshCoordinator>()
    private val runner = FeedUpdateRunner(coordinator)

    @Test
    fun `successful feed update never performs cleanup`() = runTest {
        coEvery { coordinator.refresh(FeedRefreshSource.BACKGROUND) } returns
            PodcastUpdateSummary(totalCount = 2, successfulCount = 2, failureCount = 0)

        runner()

        coVerify(exactly = 1) { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
    }

    @Test
    fun `partially failed feed update never performs cleanup`() = runTest {
        coEvery { coordinator.refresh(FeedRefreshSource.BACKGROUND) } returns
            PodcastUpdateSummary(totalCount = 2, successfulCount = 1, failureCount = 1)

        runner()

        coVerify(exactly = 1) { coordinator.refresh(FeedRefreshSource.BACKGROUND) }
    }

    @Test
    fun `backup worker routes restore refresh source`() = runTest {
        coEvery { coordinator.refresh(FeedRefreshSource.BACKUP_RESTORE) } returns
            PodcastUpdateSummary(totalCount = 1, successfulCount = 1, failureCount = 0)

        runner(FeedRefreshSource.BACKUP_RESTORE)

        coVerify(exactly = 1) { coordinator.refresh(FeedRefreshSource.BACKUP_RESTORE) }
    }

    @Test
    fun `targeted retry forwards only the failed feed URL`() = runTest {
        val feedUrls = setOf("https://example.com/retry.xml")
        coEvery {
            coordinator.refresh(FeedRefreshSource.BACKGROUND, feedUrls = feedUrls)
        } returns PodcastUpdateSummary(totalCount = 1, successfulCount = 1, failureCount = 0)

        runner(FeedRefreshSource.BACKGROUND, feedUrls)

        coVerify(exactly = 1) {
            coordinator.refresh(FeedRefreshSource.BACKGROUND, feedUrls = feedUrls)
        }
    }
}
