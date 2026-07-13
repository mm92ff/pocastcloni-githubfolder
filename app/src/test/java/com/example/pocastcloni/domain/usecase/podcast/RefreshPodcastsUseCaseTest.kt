package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshPodcastsUseCaseTest {
    private val coordinator = mockk<FeedRefreshCoordinator>()
    private val useCase = RefreshPodcastsUseCase(coordinator)

    @Test
    fun `startup refresh uses configured policy`() = runTest {
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery { coordinator.refresh(FeedRefreshSource.STARTUP, false) } returns expected

        assertEquals(expected, useCase(forceFull = false))

        coVerify(exactly = 1) { coordinator.refresh(FeedRefreshSource.STARTUP, false) }
    }

    @Test
    fun `home manual refresh requests full sync with configured download limit`() = runTest {
        val expected = PodcastUpdateSummary(1, 1, 0)
        coEvery { coordinator.refresh(FeedRefreshSource.MANUAL, true) } returns expected

        assertEquals(expected, useCase(forceFull = true))

        coVerify(exactly = 1) { coordinator.refresh(FeedRefreshSource.MANUAL, true) }
    }
}
