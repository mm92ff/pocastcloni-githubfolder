package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.domain.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderFavoritesUseCaseTest {

    private val repository: PodcastRepository = mockk()
    private val useCase = ReorderFavoritesUseCase(repository)

    @Test
    fun `reorder passes only episode ids and order timestamp`() = runTest {
        val capturedIds = slot<List<Long>>()
        val capturedTimestamp = slot<Long>()

        coEvery {
            repository.reorderFavorites(capture(capturedIds), capture(capturedTimestamp))
        } just runs

        useCase(listOf(102L, 101L))

        coVerify { repository.reorderFavorites(any(), any()) }
        coVerify(exactly = 0) { repository.getEpisode(any()) }
        assertEquals(listOf(102L, 101L), capturedIds.captured)
        check(capturedTimestamp.captured > 0L)
    }
}
