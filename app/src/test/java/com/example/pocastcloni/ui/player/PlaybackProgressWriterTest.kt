package com.example.pocastcloni.ui.player

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.usecase.player.SavePlaybackProgressUseCase
import com.example.pocastcloni.domain.usecase.stats.AddListeningTimeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressWriterTest {
    @Test
    fun `slow save stays serial and newest pending position wins`() = runTest {
        val repository = mockk<PodcastRepository>()
        val firstSaveGate = CompletableDeferred<Unit>()
        val savedPositions = mutableListOf<Long>()
        var activeSaves = 0
        var maximumActiveSaves = 0

        coEvery { repository.savePlaybackProgress(GUID, any()) } coAnswers {
            activeSaves += 1
            maximumActiveSaves = maxOf(maximumActiveSaves, activeSaves)
            val position = secondArg<Long>()
            savedPositions += position
            if (position == 100L) firstSaveGate.await()
            activeSaves -= 1
        }

        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(GUID, 100L)
        runCurrent()
        writer.request(GUID, 200L)
        writer.request(GUID, 300L)
        firstSaveGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(100L, 300L), savedPositions)
        assertEquals(1, maximumActiveSaves)
    }

    @Test
    fun `failed position is retried by the next flush request`() = runTest {
        val repository = mockk<PodcastRepository>()
        var attempts = 0
        coEvery { repository.savePlaybackProgress(GUID, 400L) } coAnswers {
            attempts += 1
            if (attempts == 1) error("temporary failure")
        }
        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(GUID, 400L)
        runCurrent()
        writer.request(GUID, 400L)
        runCurrent()

        assertEquals(2, attempts)
        coVerify(exactly = 2) { repository.savePlaybackProgress(GUID, 400L) }
    }

    @Test
    fun `failed final position retries even without a newer request`() = runTest {
        val repository = mockk<PodcastRepository>()
        var attempts = 0
        coEvery { repository.savePlaybackProgress(GUID, 450L) } coAnswers {
            attempts += 1
            if (attempts == 1) error("temporary failure")
        }
        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(GUID, 450L)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(PlaybackProgressWriter.INITIAL_RETRY_DELAY_MS)
        runCurrent()

        assertEquals(2, attempts)
        coVerify(exactly = 2) { repository.savePlaybackProgress(GUID, 450L) }
    }

    @Test
    fun `listening time is restored after a failed write`() = runTest {
        val repository = mockk<StatisticsRepository>(relaxed = true)
        var attempts = 0
        coEvery { repository.addListeningTime(1_500L) } coAnswers {
            attempts += 1
            if (attempts == 1) error("temporary failure")
        }
        val writer =
            PlaybackListeningTimeWriter(
                addListeningTime = AddListeningTimeUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.recordAndFlush(1_500L)
        runCurrent()
        writer.recordAndFlush(0L)
        runCurrent()

        assertEquals(2, attempts)
    }

    private companion object {
        private const val GUID = "episode-guid"
    }
}
