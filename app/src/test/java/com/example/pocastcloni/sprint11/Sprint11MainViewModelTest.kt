package com.example.pocastcloni.sprint11

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.ui.main.MainViewModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint11MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `settings failure keeps defaults and retry clears the error`() = runTest(dispatcher) {
        val getSettings = mockk<GetUserSettingsUseCase>()
        val recovered = UserSettings(appColor = AppColor.BLUE)
        var subscriptions = 0
        every { getSettings() } answers {
            subscriptions++
            if (subscriptions == 1) {
                flow { throw IllegalStateException("settings unavailable") }
            } else {
                flowOf(recovered)
            }
        }
        val viewModel = createViewModel(getSettings)

        viewModel.uiState.test {
            assertEquals(UserSettings(), awaitItem().userSettings)

            val failed = awaitItem()
            assertEquals(UserSettings(), failed.userSettings)
            assertNotNull(failed.error)
            assertFalse(failed.isLoading)

            viewModel.retrySettings()

            val successful = awaitItem()
            assertEquals(recovered, successful.userSettings)
            assertNull(successful.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, subscriptions)
    }

    @Test
    fun `settings failure retains the last successful value`() = runTest(dispatcher) {
        val retained = UserSettings(appColor = AppColor.RED)
        val getSettings = mockk<GetUserSettingsUseCase>()
        every { getSettings() } returns
            flow {
                emit(retained)
                throw IllegalStateException("transient")
            }
        val viewModel = createViewModel(getSettings)

        viewModel.uiState.test {
            awaitItem()
            assertEquals(retained, awaitItem().userSettings)

            val failed = awaitItem()
            assertEquals(retained, failed.userSettings)
            assertNotNull(failed.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed retry keeps the value from the previous subscription`() = runTest(dispatcher) {
        val retained = UserSettings(appColor = AppColor.PURPLE)
        val getSettings = mockk<GetUserSettingsUseCase>()
        var subscriptions = 0
        every { getSettings() } answers {
            subscriptions++
            if (subscriptions == 1) {
                flowOf(retained)
            } else {
                flow { throw IllegalStateException("retry failed") }
            }
        }
        val viewModel = createViewModel(getSettings)

        viewModel.uiState.test {
            awaitItem()
            assertEquals(retained, awaitItem().userSettings)

            viewModel.retrySettings()

            val failedRetry = awaitItem()
            assertEquals(retained, failedRetry.userSettings)
            assertNotNull(failedRetry.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(getSettings: GetUserSettingsUseCase): MainViewModel =
        MainViewModel(
            getUserSettings = getSettings,
            playerController = mockk<AudioPlayerController>(relaxed = true),
            dispatcherProvider = mockk<DispatcherProvider>(relaxed = true)
        )
}
