package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction.*
import com.example.pocastcloni.ui.settings.AppColor
import com.example.pocastcloni.ui.settings.AppTheme
import com.example.pocastcloni.ui.settings.BufferMode
import com.example.pocastcloni.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateUserSettingsUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: UserPreferencesRepository
    private lateinit var backgroundWorker: UpdateBackgroundWorkerUseCase
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var useCase: UpdateUserSettingsUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        backgroundWorker = mockk(relaxed = true)
        dispatcherProvider = mockk()
        every { dispatcherProvider.io } returns testDispatcher
        every { repository.userSettingsFlow } returns flowOf(UserSettings())
        useCase = UpdateUserSettingsUseCase(repository, backgroundWorker, dispatcherProvider)
    }

    // --- Appearance ---

    @Test
    fun `SetAppTheme calls updateTheme`() = runTest(testDispatcher) {
        useCase(SetAppTheme(AppTheme.DARK))
        coVerify { repository.updateTheme(AppTheme.DARK) }
    }

    @Test
    fun `SetAppColor calls updateAppColor`() = runTest(testDispatcher) {
        useCase(SetAppColor(AppColor.GREEN))
        coVerify { repository.updateAppColor(AppColor.GREEN) }
    }

    @Test
    fun `SetColorStrength calls updateColorStrength`() = runTest(testDispatcher) {
        useCase(SetColorStrength(0.7f))
        coVerify { repository.updateColorStrength(0.7f) }
    }

    @Test
    fun `SetBufferSettings calls updateBufferSettings`() = runTest(testDispatcher) {
        useCase(SetBufferSettings(BufferMode.MAXIMAL))
        coVerify { repository.updateBufferSettings(BufferMode.MAXIMAL) }
    }

    // --- Layout ---

    @Test
    fun `SetLayoutMode calls updateLayoutMode`() = runTest(testDispatcher) {
        useCase(SetLayoutMode(LayoutMode.LIST))
        coVerify { repository.updateLayoutMode(LayoutMode.LIST) }
    }

    @Test
    fun `SetGridSize calls updateGridSize`() = runTest(testDispatcher) {
        useCase(SetGridSize(3))
        coVerify { repository.updateGridSize(3) }
    }

    @Test
    fun `ToggleShowGridTitles calls updateShowGridTitles`() = runTest(testDispatcher) {
        useCase(ToggleShowGridTitles(false))
        coVerify { repository.updateShowGridTitles(false) }
    }

    @Test
    fun `ToggleMiniPlayerTimeOverlay calls updateShowMiniPlayerTimeOverlay`() = runTest(testDispatcher) {
        useCase(ToggleMiniPlayerTimeOverlay(true))
        coVerify { repository.updateShowMiniPlayerTimeOverlay(true) }
    }

    @Test
    fun `ToggleBottomBarCleanMode calls updateBottomBarCleanModeEnabled`() = runTest(testDispatcher) {
        useCase(ToggleBottomBarCleanMode(true))
        coVerify { repository.updateBottomBarCleanModeEnabled(true) }
    }

    @Test
    fun `ToggleBottomBarAutoHide calls updateBottomBarAutoHideEnabled`() = runTest(testDispatcher) {
        useCase(ToggleBottomBarAutoHide(true))
        coVerify { repository.updateBottomBarAutoHideEnabled(true) }
    }

    @Test
    fun `SetBottomBarAutoHideDelay calls updateBottomBarAutoHideDelaySeconds`() = runTest(testDispatcher) {
        useCase(SetBottomBarAutoHideDelay(10))
        coVerify { repository.updateBottomBarAutoHideDelaySeconds(10) }
    }

    @Test
    fun `ToggleGradientBackground calls updateGradientBackgroundEnabled`() = runTest(testDispatcher) {
        useCase(ToggleGradientBackground(true))
        coVerify { repository.updateGradientBackgroundEnabled(true) }
    }

    @Test
    fun `SetGradientBackgroundStrength calls updateGradientBackgroundStrength`() = runTest(testDispatcher) {
        useCase(SetGradientBackgroundStrength(0.4f))
        coVerify { repository.updateGradientBackgroundStrength(0.4f) }
    }

    @Test
    fun `ToggleTransparentSearchCards calls updateTransparentSearchCards`() = runTest(testDispatcher) {
        useCase(ToggleTransparentSearchCards(true))
        coVerify { repository.updateTransparentSearchCards(true) }
    }

    @Test
    fun `ToggleTransparentEpisodeRows calls updateTransparentEpisodeRows`() = runTest(testDispatcher) {
        useCase(ToggleTransparentEpisodeRows(true))
        coVerify { repository.updateTransparentEpisodeRows(true) }
    }

    // --- Automation ---

    @Test
    fun `ToggleBackgroundCheck calls updateBackgroundCheckEnabled and triggers worker sync`() = runTest(testDispatcher) {
        useCase(ToggleBackgroundCheck(true))
        coVerify { repository.updateBackgroundCheckEnabled(true) }
        verify { backgroundWorker(any(), any()) }
    }

    @Test
    fun `SetBackgroundCheckInterval calls updateBackgroundCheckInterval and triggers worker sync`() = runTest(testDispatcher) {
        useCase(SetBackgroundCheckInterval(6))
        coVerify { repository.updateBackgroundCheckInterval(6) }
        verify { backgroundWorker(any(), any()) }
    }

    @Test
    fun `SetFeedUpdateMode does NOT trigger background worker sync`() = runTest(testDispatcher) {
        useCase(SetFeedUpdateMode(FeedUpdateMode.SMART_STREAM))
        coVerify { repository.updateFeedUpdateMode(FeedUpdateMode.SMART_STREAM) }
        verify(exactly = 0) { backgroundWorker(any(), any()) }
    }

    // --- Cleanup ---

    @Test
    fun `ToggleAutoCleanup calls updateAutoCleanupEnabled`() = runTest(testDispatcher) {
        useCase(ToggleAutoCleanup(false))
        coVerify { repository.updateAutoCleanupEnabled(false) }
    }

    @Test
    fun `SetCleanupKeepLimit calls updateCleanupKeepLimit`() = runTest(testDispatcher) {
        useCase(SetCleanupKeepLimit(100))
        coVerify { repository.updateCleanupKeepLimit(100) }
    }

    @Test
    fun `SetCleanupIntervalHours calls updateCleanupIntervalHours`() = runTest(testDispatcher) {
        useCase(SetCleanupIntervalHours(48))
        coVerify { repository.updateCleanupIntervalHours(48) }
    }

    @Test
    fun `ToggleAutoCleanup does NOT trigger background feed worker sync`() = runTest(testDispatcher) {
        useCase(ToggleAutoCleanup(true))
        verify(exactly = 0) { backgroundWorker(any(), any()) }
    }

    // --- Downloads ---

    @Test
    fun `SetAutoDownloadLimit calls updateAutoDownloadLimit`() = runTest(testDispatcher) {
        useCase(SetAutoDownloadLimit(5))
        coVerify { repository.updateAutoDownloadLimit(5) }
    }

    @Test
    fun `ToggleSaveToDownloadsFolder calls updateSaveToDownloadsFolder`() = runTest(testDispatcher) {
        useCase(ToggleSaveToDownloadsFolder(true))
        coVerify { repository.updateSaveToDownloadsFolder(true) }
    }
}
