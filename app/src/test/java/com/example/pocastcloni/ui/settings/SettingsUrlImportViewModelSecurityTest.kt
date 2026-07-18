package com.example.pocastcloni.ui.settings

import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.usecase.podcast.AddPodcastFromUrlUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsUrlImportViewModelSecurityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = StandardTestDispatcher()
    private val addPodcast = mockk<AddPodcastFromUrlUseCase>(relaxed = true)
    private val dispatcherProvider = mockk<DispatcherProvider>().also {
        every { it.io } returns dispatcher
    }
    private val viewModel = SettingsUrlImportViewModel(addPodcast, dispatcherProvider)

    @Test
    fun `HTTP feed requires an explicit legacy media approval`() = runTest(dispatcher) {
        val url = "http://example.com/feed.xml"
        viewModel.onUrlChange(url)

        viewModel.onAddPodcast()

        assertEquals(url, viewModel.uiState.value.pendingCleartextConfirmationUrl)
        coVerify(exactly = 0) { addPodcast(any(), any(), any()) }

        viewModel.setAllowInsecureHttp(true)
        viewModel.onAddPodcast()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            addPodcast(url, allowInsecureHttp = true, allowLocalNetwork = false)
        }
        assertNull(viewModel.uiState.value.pendingCleartextConfirmationUrl)
    }

    @Test
    fun `HTTPS feed proceeds without confirmation`() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        viewModel.onUrlChange(url)

        viewModel.onAddPodcast()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            addPodcast(url, allowInsecureHttp = false, allowLocalNetwork = false)
        }
        assertNull(viewModel.uiState.value.pendingCleartextConfirmationUrl)
    }

    @Test
    fun `local HTTPS feed requires explicit local approval`() = runTest(dispatcher) {
        val url = "https://192.168.1.20/feed.xml"
        viewModel.onUrlChange(url)

        viewModel.onAddPodcast()

        assertEquals(url, viewModel.uiState.value.pendingLocalNetworkConfirmationUrl)
        coVerify(exactly = 0) { addPodcast(any(), any(), any()) }

        viewModel.setAllowLocalNetwork(true)
        viewModel.onAddPodcast()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            addPodcast(url, allowInsecureHttp = false, allowLocalNetwork = true)
        }
    }

    @Test
    fun `local HTTP feed requires both approvals`() = runTest(dispatcher) {
        val url = "http://10.0.2.2/feed.xml"
        viewModel.onUrlChange(url)
        viewModel.setAllowLocalNetwork(true)

        viewModel.onAddPodcast()

        assertEquals(url, viewModel.uiState.value.pendingCleartextConfirmationUrl)
        coVerify(exactly = 0) { addPodcast(any(), any(), any()) }

        viewModel.setAllowInsecureHttp(true)
        viewModel.onAddPodcast()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            addPodcast(url, allowInsecureHttp = true, allowLocalNetwork = true)
        }
    }

    @Test
    fun `add failure does not expose localized exception text`() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        coEvery { addPodcast(url, false, false) } throws
            IllegalStateException("Podcast konnte nicht hinzugefügt werden")
        viewModel.onUrlChange(url)

        viewModel.onAddPodcast()
        advanceUntilIdle()

        assertEquals(
            UiText.StringResource(R.string.error_add_podcast_failed),
            viewModel.uiState.value.message
        )
    }
}
