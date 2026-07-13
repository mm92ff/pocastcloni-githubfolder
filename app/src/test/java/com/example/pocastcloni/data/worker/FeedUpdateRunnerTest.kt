package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FeedUpdateRunnerTest {
    private val repository = mockk<PodcastRepository>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val runner = FeedUpdateRunner(repository, preferences)

    @Test
    fun `successful feed update never performs cleanup`() = runTest {
        givenSettings()
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } returns
            PodcastUpdateSummary(totalCount = 2, successfulCount = 2, failureCount = 0)

        runner()

        coVerify(exactly = 0) { repository.cleanupPlayedEpisodes() }
    }

    @Test
    fun `partially failed feed update never performs cleanup`() = runTest {
        givenSettings()
        coEvery { repository.updateAllPodcasts(3, FeedUpdateMode.SMART_STREAM, false) } returns
            PodcastUpdateSummary(totalCount = 2, successfulCount = 1, failureCount = 1)

        runner()

        coVerify(exactly = 0) { repository.cleanupPlayedEpisodes() }
    }

    private fun givenSettings() {
        every { preferences.userSettingsFlow } returns
            flowOf(UserSettings(autoDownloadLimit = 3, feedUpdateMode = FeedUpdateMode.SMART_STREAM))
    }
}
