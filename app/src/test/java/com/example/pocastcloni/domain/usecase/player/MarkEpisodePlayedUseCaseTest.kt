package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarkEpisodePlayedUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: MarkEpisodePlayedUseCase

    private val testDispatcher = StandardTestDispatcher()
    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        dispatcherProvider = mockk()
        io.mockk.every { dispatcherProvider.io } returns testDispatcher
        useCase = MarkEpisodePlayedUseCase(repository, dispatcherProvider)
    }

    @Test
    fun `marks episode without reading stale podcast snapshots`() = runTest(testDispatcher) {
        useCase(101L)

        coVerify(exactly = 1) { repository.markEpisodePlayed(101L, true, any()) }
        coVerify(exactly = 0) { repository.getEpisode(any()) }
        coVerify(exactly = 0) { repository.getEpisodesForSync(any()) }
        coVerify(exactly = 0) { repository.getPodcastEntityByUrl(any()) }
    }
}
