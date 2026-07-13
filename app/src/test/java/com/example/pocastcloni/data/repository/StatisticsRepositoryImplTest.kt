package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.domain.repository.AppStatistics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StatisticsRepositoryImplTest {
    private val dataStore = mockk<StatisticsDataStore>(relaxed = true)
    private val podcastDao = mockk<PodcastDao>(relaxed = true)
    private val streamingRecorder = mockk<StreamingStatisticsRecorder>()

    @Test
    fun `statistics reset clears counters without changing history or progress`() = runTest {
        every { dataStore.statsFlow } returns flowOf(AppStatistics())
        every { podcastDao.getTotalEpisodeCount() } returns flowOf(3)
        every { podcastDao.getEpisodesInProgressCount() } returns flowOf(1)
        every { podcastDao.getPlayedEpisodesCount() } returns flowOf(2)
        coEvery { streamingRecorder.resetStatistics(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        val repository =
            StatisticsRepositoryImpl(
                dataStore = dataStore,
                podcastDao = podcastDao,
                streamingRecorder = streamingRecorder,
                appScope = backgroundScope
            )

        repository.resetStatistics()

        coVerify(exactly = 1) { dataStore.resetStatistics() }
        coVerify(exactly = 0) { podcastDao.clearHistory() }
    }
}
