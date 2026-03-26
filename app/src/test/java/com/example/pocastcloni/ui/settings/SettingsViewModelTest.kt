package com.example.pocastcloni.ui.settings

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.player.PlayerVisibilityProvider
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.AppMaintenanceUseCases
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingsUseCase
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var getUserSettings: GetUserSettingsUseCase
    private lateinit var updateUserSettings: UpdateUserSettingsUseCase
    private lateinit var appMaintenanceUseCases: AppMaintenanceUseCases
    private lateinit var playerVisibilityProvider: PlayerVisibilityProvider
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var viewModel: SettingsViewModel

    private val defaultSettings = UserSettings(
        autoCleanupEnabled = true,
        cleanupKeepLimit = 50,
        cleanupIntervalHours = 24,
        backgroundCheckEnabled = false,
        backgroundCheckInterval = 12,
        indicator = IndicatorSettings()
    )

    @Before
    fun setup() {
        getUserSettings = mockk()
        updateUserSettings = mockk(relaxed = true)
        appMaintenanceUseCases = mockk(relaxed = true)
        playerVisibilityProvider = mockk()
        dispatcherProvider = mockk()

        every { getUserSettings() } returns flowOf(defaultSettings)
        every { playerVisibilityProvider.isPlayerVisible } returns MutableStateFlow(false)
        every { dispatcherProvider.io } returns testDispatcher

        viewModel = SettingsViewModel(
            getUserSettings = getUserSettings,
            appMaintenanceUseCases = appMaintenanceUseCases,
            updateUserSettings = updateUserSettings,
            playerVisibilityProvider = playerVisibilityProvider,
            dispatcherProvider = dispatcherProvider
        )
    }

    // ---- toUiState mapping ----

    @Test
    fun `uiState maps autoCleanupEnabled from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading state
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.autoCleanupEnabled)
        }
    }

    @Test
    fun `uiState maps cleanupKeepLimit from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertEquals(50, success.cleanupKeepLimit)
        }
    }

    @Test
    fun `uiState maps cleanupIntervalHours from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertEquals(24, success.cleanupIntervalHours)
        }
    }

    // ---- shouldDebounce ----

    @Test
    fun `SetCleanupKeepLimit action is debounced`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.SetCleanupKeepLimit(75)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `SetCleanupIntervalHours action is debounced`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.SetCleanupIntervalHours(48)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleAutoCleanup action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleAutoCleanup(false)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    // ---- reset dialog ----

    @Test
    fun `OnResetClicked shows reset dialog`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            awaitItem() // settings loaded
            viewModel.onEvent(SettingsUiEvent.OnResetClicked)
            val state = awaitItem()
            assertTrue(state.showResetDialog)
        }
    }

    @Test
    fun `OnResetDismissed hides reset dialog`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            viewModel.onEvent(SettingsUiEvent.OnResetClicked)
            awaitItem() // dialog shown
            viewModel.onEvent(SettingsUiEvent.OnResetDismissed)
            val state = awaitItem()
            assertFalse(state.showResetDialog)
        }
    }
}
