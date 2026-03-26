package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateUserSettingsUseCase
@Inject
constructor(
    private val repository: UserPreferencesRepository,
    private val updateBackgroundWorker: UpdateBackgroundWorkerUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(action: UpdateUserSettingAction) {
        withContext(dispatcherProvider.io) {
            // 1. Save setting to the repository
            when (action) {
                is SetAppTheme -> repository.updateTheme(action.theme)
                is SetAppColor -> repository.updateAppColor(action.color)
                is SetColorStrength -> repository.updateColorStrength(action.strength)
                is SetBufferSettings -> repository.updateBufferSettings(action.mode)

                is SetLayoutMode -> repository.updateLayoutMode(action.mode)

                is SetGridSize -> repository.updateGridSize(action.size)
                is ToggleShowGridTitles -> repository.updateShowGridTitles(action.show)
                is ToggleConfirmDelete -> repository.updateConfirmDelete(action.confirm)
                is SetProgressBarHeight -> repository.updateProgressBarHeight(action.height)
                is SetNavBarHeight -> repository.updateNavBarHeight(action.height)
                is ToggleOneHandedMode -> repository.updateOneHandedMode(action.enabled)
                is SetAutoDownloadLimit -> repository.updateAutoDownloadLimit(action.limit)
                is ToggleAutoRefreshOnStart -> repository.updateAutoRefreshOnStart(action.enabled)
                is SetMarkPlayedDuration -> repository.updateMarkPlayedDuration(action.seconds)
                is SetFeedUpdateMode -> repository.updateFeedUpdateMode(action.mode)

                is SetIndicatorColor -> repository.updateIndicatorColor(action.colorArgb)
                is SetIndicatorSize -> repository.updateIndicatorSize(action.sizeDp)
                is SetIndicatorBorderWidth -> repository.updateIndicatorBorderWidth(action.widthDp)
                is SetIndicatorXOffset -> repository.updateIndicatorXOffset(action.offsetDp)
                is SetIndicatorYOffset -> repository.updateIndicatorYOffset(action.offsetDp)

                // Worker-relevant settings
                is ToggleBackgroundCheck -> repository.updateBackgroundCheckEnabled(action.enabled)
                is SetBackgroundCheckInterval -> repository.updateBackgroundCheckInterval(action.hours)
                is ToggleSaveToDownloadsFolder -> repository.updateSaveToDownloadsFolder(action.enabled)

                is ToggleAutoCleanup -> repository.updateAutoCleanupEnabled(action.enabled)
                is SetCleanupKeepLimit -> repository.updateCleanupKeepLimit(action.limit)
                is SetCleanupIntervalHours -> repository.updateCleanupIntervalHours(action.hours)
            }

            // 2. Side-effects: sync the worker if needed
            if (action is ToggleBackgroundCheck || action is SetBackgroundCheckInterval) {
                syncBackgroundWorker()
            }
            if (action is ToggleAutoCleanup || action is SetCleanupIntervalHours) {
                syncCleanupWorker()
            }
        }
    }

    private suspend fun syncBackgroundWorker() {
        // Read the source of truth: fetch the current state from Preferences
        // to ensure we always work with consistent data (interval + enabled flag)
        val settings = repository.userSettingsFlow.first()
        updateBackgroundWorker(settings.backgroundCheckEnabled, settings.backgroundCheckInterval)
    }

    private suspend fun syncCleanupWorker() {
        // AppInitializer's reactive collector will pick up the change automatically
        // because it observes userSettingsFlow; no explicit re-schedule needed here.
    }
}
