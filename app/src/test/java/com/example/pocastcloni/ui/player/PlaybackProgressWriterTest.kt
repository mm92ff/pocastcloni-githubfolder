package com.example.pocastcloni.ui.player

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.usecase.player.SavePlaybackProgressUseCase
import com.example.pocastcloni.domain.usecase.stats.AddListeningTimeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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

        coEvery { repository.savePlaybackProgress(EPISODE_ID, any()) } coAnswers {
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

        writer.request(EPISODE_ID, 100L)
        runCurrent()
        writer.request(EPISODE_ID, 200L)
        writer.request(EPISODE_ID, 300L)
        firstSaveGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(100L, 300L), savedPositions)
        assertEquals(1, maximumActiveSaves)
    }

    @Test
    fun `slow write preserves latest pending snapshot for every episode`() = runTest {
        val repository = mockk<PodcastRepository>()
        val firstSaveGate = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<Long, Long>>()
        coEvery { repository.savePlaybackProgress(any(), any()) } coAnswers {
            val write = firstArg<Long>() to secondArg<Long>()
            writes += write
            if (write == (EPISODE_ID to 100L)) firstSaveGate.await()
        }
        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(EPISODE_ID, 100L)
        runCurrent()
        writer.request(EPISODE_ID, 200L)
        writer.request(OTHER_EPISODE_ID, 300L)
        firstSaveGate.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(
                EPISODE_ID to 100L,
                EPISODE_ID to 200L,
                OTHER_EPISODE_ID to 300L
            ),
            writes
        )
    }

    @Test
    fun `failed position self retries without another request`() = runTest {
        val repository = mockk<PodcastRepository>()
        var attempts = 0
        coEvery { repository.savePlaybackProgress(EPISODE_ID, 400L) } coAnswers {
            attempts += 1
            if (attempts == 1) error("temporary failure")
        }
        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(EPISODE_ID, 400L)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(PlaybackProgressWriter.INITIAL_RETRY_DELAY_MS)
        runCurrent()

        assertEquals(2, attempts)
        coVerify(exactly = 2) { repository.savePlaybackProgress(EPISODE_ID, 400L) }
    }

    @Test
    fun `failed final position retries even without a newer request`() = runTest {
        val repository = mockk<PodcastRepository>()
        var attempts = 0
        coEvery { repository.savePlaybackProgress(EPISODE_ID, 450L) } coAnswers {
            attempts += 1
            if (attempts == 1) error("temporary failure")
        }
        val writer =
            PlaybackProgressWriter(
                savePlaybackProgress = SavePlaybackProgressUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.request(EPISODE_ID, 450L)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(PlaybackProgressWriter.INITIAL_RETRY_DELAY_MS)
        runCurrent()

        assertEquals(2, attempts)
        coVerify(exactly = 2) { repository.savePlaybackProgress(EPISODE_ID, 450L) }
    }

    @Test
    fun `listening time self retries and aggregates values received during backoff`() = runTest {
        val repository = mockk<StatisticsRepository>(relaxed = true)
        val attempts = mutableListOf<Long>()
        coEvery { repository.addListeningTime(any()) } coAnswers {
            attempts += firstArg<Long>()
            if (attempts.size == 1) error("temporary failure")
        }
        val writer =
            PlaybackListeningTimeWriter(
                addListeningTime = AddListeningTimeUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.recordAndFlush(1_500L)
        runCurrent()
        writer.recordAndFlush(500L)
        advanceTimeBy(PlaybackListeningTimeWriter.INITIAL_RETRY_DELAY_MS)
        runCurrent()

        assertEquals(listOf(1_500L, 2_000L), attempts)
    }

    @Test
    fun `listening time cancellation retains data but stops retries`() = runTest {
        val repository = mockk<StatisticsRepository>(relaxed = true)
        var attempts = 0
        coEvery { repository.addListeningTime(any()) } coAnswers {
            attempts += 1
            throw CancellationException("scope stopped")
        }
        val writer =
            PlaybackListeningTimeWriter(
                addListeningTime = AddListeningTimeUseCase(repository),
                applicationScope = backgroundScope
            )

        writer.recordAndFlush(1_000L)
        runCurrent()
        advanceTimeBy(PlaybackListeningTimeWriter.MAX_RETRY_DELAY_MS)
        writer.recordAndFlush(500L)
        runCurrent()

        assertEquals(1, attempts)
    }

    private companion object {
        private const val EPISODE_ID = 101L
        private const val OTHER_EPISODE_ID = 202L
    }
}
