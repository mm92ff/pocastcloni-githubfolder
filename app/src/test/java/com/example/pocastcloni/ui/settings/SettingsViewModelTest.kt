package com.example.pocastcloni.ui.settings

import app.cash.turbine.test
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.playback.api.PlayerVisibilityProvider
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.AppMaintenanceUseCases
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingsUseCase
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

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
        homeBottomSpacing = 12,
        showMiniPlayerTimeOverlay = true,
        bottomBarCleanModeEnabled = true,
        bottomBarAutoHideEnabled = true,
        bottomBarAutoHideDelaySeconds = 7,
        gradientBackgroundEnabled = true,
        gradientBackgroundStrength = 0.6f,
        transparentSearchCards = true,
        transparentEpisodeRows = true,
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
    fun `uiState maps homeBottomSpacing from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertEquals(12, success.homeBottomSpacing)
        }
    }

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

    @Test
    fun `uiState maps showMiniPlayerTimeOverlay from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.showMiniPlayerTimeOverlay)
        }
    }

    @Test
    fun `uiState maps bottomBarCleanModeEnabled from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.bottomBarCleanModeEnabled)
        }
    }

    @Test
    fun `uiState maps bottomBarAutoHide settings from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.bottomBarAutoHideEnabled)
            assertEquals(7, success.bottomBarAutoHideDelaySeconds)
        }
    }

    @Test
    fun `uiState maps gradientBackgroundEnabled from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.gradientBackgroundEnabled)
        }
    }

    @Test
    fun `uiState maps gradientBackgroundStrength from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertEquals(0.6f, success.gradientBackgroundStrength, 0.001f)
        }
    }

    @Test
    fun `uiState maps transparentSearchCards from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.transparentSearchCards)
        }
    }

    @Test
    fun `uiState maps transparentEpisodeRows from UserSettings`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val success = state.settings as? SettingsUiState.Success ?: return@test
            assertTrue(success.transparentEpisodeRows)
        }
    }

    // ---- keyed debounce ----

    @Test
    fun `different debounced action types persist independently`() = runTest(testDispatcher) {
        runCurrent()
        val gridSize = UpdateUserSettingAction.SetGridSize(140)
        val navBarHeight = UpdateUserSettingAction.SetNavBarHeight(64)

        viewModel.onEvent(SettingsUiEvent.UpdateSetting(gridSize))
        advanceTimeBy(100)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(navBarHeight))

        advanceTimeBy(200)
        runCurrent()
        coVerify(exactly = 1) { updateUserSettings(gridSize) }
        coVerify(exactly = 0) { updateUserSettings(navBarHeight) }

        advanceTimeBy(100)
        runCurrent()
        coVerify(exactly = 1) { updateUserSettings(navBarHeight) }
    }

    @Test
    fun `same debounced action type persists only latest value`() = runTest(testDispatcher) {
        runCurrent()
        val earlier = UpdateUserSettingAction.SetGridSize(120)
        val latest = UpdateUserSettingAction.SetGridSize(160)

        viewModel.onEvent(SettingsUiEvent.UpdateSetting(earlier))
        advanceTimeBy(100)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(latest))
        advanceTimeBy(300)
        runCurrent()

        coVerify(exactly = 0) { updateUserSettings(earlier) }
        coVerify(exactly = 1) { updateUserSettings(latest) }
    }

    @Test
    fun `immediate toggle bypasses pending debounce timer`() = runTest(testDispatcher) {
        runCurrent()
        val slider = UpdateUserSettingAction.SetGridSize(150)
        val toggle = UpdateUserSettingAction.ToggleShowGridTitles(false)

        viewModel.onEvent(SettingsUiEvent.UpdateSetting(slider))
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(toggle))
        runCurrent()

        coVerify(exactly = 1) { updateUserSettings(toggle) }
        coVerify(exactly = 0) { updateUserSettings(slider) }

        advanceTimeBy(300)
        runCurrent()
        coVerify(exactly = 1) { updateUserSettings(slider) }
    }

    @Test
    fun `same value can retry after failed persistence`() = runTest(testDispatcher) {
        val action = UpdateUserSettingAction.SetGridSize(150)
        var attempts = 0
        coEvery { updateUserSettings(action) } answers {
            attempts += 1
            if (attempts == 1) throw IOException("write failed")
        }

        viewModel.effects.test {
            runCurrent()
            viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
            advanceTimeBy(300)
            runCurrent()
            assertTrue(awaitItem() is SettingsViewModel.SettingsUiEffect.Snackbar)

            viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
            advanceTimeBy(300)
            runCurrent()

            coVerify(exactly = 2) { updateUserSettings(action) }
            expectNoEvents()
        }
    }

    @Test
    fun `persistence cancellation is not reported as failure`() = runTest(testDispatcher) {
        val action = UpdateUserSettingAction.SetGridSize(150)
        coEvery { updateUserSettings(action) } throws CancellationException("cancelled")

        viewModel.effects.test {
            runCurrent()
            viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
            advanceTimeBy(300)
            runCurrent()

            coVerify(exactly = 1) { updateUserSettings(action) }
            expectNoEvents()
        }
    }

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
    fun `SetGradientBackgroundStrength action is debounced`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.SetGradientBackgroundStrength(0.4f)
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

    @Test
    fun `ToggleMiniPlayerTimeOverlay action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleMiniPlayerTimeOverlay(true)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleBottomBarCleanMode action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleBottomBarCleanMode(true)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleBottomBarAutoHide action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleBottomBarAutoHide(true)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `SetBottomBarAutoHideDelay action is debounced`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.SetBottomBarAutoHideDelay(10)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleGradientBackground action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleGradientBackground(true)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleTransparentSearchCards action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleTransparentSearchCards(true)
        viewModel.onEvent(SettingsUiEvent.UpdateSetting(action))
        advanceUntilIdle()
        coVerify { updateUserSettings(action) }
    }

    @Test
    fun `ToggleTransparentEpisodeRows action is NOT debounced (instant)`() = runTest(testDispatcher) {
        advanceUntilIdle() // let the SharedFlow collector start
        val action = UpdateUserSettingAction.ToggleTransparentEpisodeRows(true)
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
