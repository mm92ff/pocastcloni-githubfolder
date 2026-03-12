package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateUserSettingsUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
    private val updateBackgroundWorker: UpdateBackgroundWorkerUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(action: UpdateUserSettingAction) {
        withContext(dispatcherProvider.io) {
            // 1. Einstellung in der Datenbank speichern
            when (action) {
                is SetAppTheme -> repository.updateTheme(action.theme)
                is SetAppColor -> repository.updateAppColor(action.color)
                is SetColorStrength -> repository.updateColorStrength(action.strength)
                is SetBufferSettings -> repository.updateBufferSettings(action.mode)

                // NEU: Layout Mode speichern
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

                // Logik für Worker-relevante Settings
                is ToggleBackgroundCheck -> repository.updateBackgroundCheckEnabled(action.enabled)
                is SetBackgroundCheckInterval -> repository.updateBackgroundCheckInterval(action.hours)
            }

            // 2. Side-Effects: Worker synchronisieren, falls nötig
            // Wir prüfen, ob die Action den Worker betrifft
            if (action is ToggleBackgroundCheck || action is SetBackgroundCheckInterval) {
                syncBackgroundWorker()
            }
        }
    }

    private suspend fun syncBackgroundWorker() {
        // "Source of Truth" lesen: Wir holen den aktuellen Stand aus den Preferences
        // Das stellt sicher, dass wir immer mit konsistenten Daten (Intervall + Enabled) arbeiten
        val settings = repository.userSettingsFlow.first()
        updateBackgroundWorker(settings.backgroundCheckEnabled, settings.backgroundCheckInterval)
    }
}