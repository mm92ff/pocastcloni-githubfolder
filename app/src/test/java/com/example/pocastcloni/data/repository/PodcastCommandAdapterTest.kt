package com.example.pocastcloni.data.repository

import android.content.Context
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.DispatcherProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
}
