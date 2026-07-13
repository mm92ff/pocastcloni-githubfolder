package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshCoordinator
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import com.example.pocastcloni.util.Constants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualFeedUpdateUseCaseTest {
    @Test
    fun `settings full refresh keeps auto downloads disabled`() = runTest {
        val coordinator = mockk<FeedRefreshCoordinator>()
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery {
            coordinator.refresh(
                FeedRefreshSource.MANUAL,
                forceFull = true,
                downloadLimitOverride = Constants.Preferences.NO_DOWNLOAD_LIMIT
            )
        } returns expected

        val result = ManualFeedUpdateUseCase(coordinator)()

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            coordinator.refresh(
                FeedRefreshSource.MANUAL,
                forceFull = true,
                downloadLimitOverride = Constants.Preferences.NO_DOWNLOAD_LIMIT
            )
        }
    }
}
