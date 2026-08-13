package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateUserSettingsUseCase
@Inject
constructor(
    private val repository: UserPreferencesRepository,
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
                is SetHomeBottomSpacing -> repository.updateHomeBottomSpacing(action.spacingDp)
                is ToggleMiniPlayerTimeOverlay -> repository.updateShowMiniPlayerTimeOverlay(action.enabled)
                is ToggleTransparentMiniPlayer -> repository.updateTransparentMiniPlayer(action.enabled)
                is ToggleTransparentBottomBar -> repository.updateTransparentBottomBar(action.enabled)
                is ToggleOneHandedMode -> repository.updateOneHandedMode(action.enabled)
                is ToggleBottomBarCleanMode -> repository.updateBottomBarCleanModeEnabled(action.enabled)
                is ToggleBottomBarAutoHide -> repository.updateBottomBarAutoHideEnabled(action.enabled)
                is SetBottomBarAutoHideDelay -> repository.updateBottomBarAutoHideDelaySeconds(action.seconds)
                is SetBottomBarRevealHandleHeight -> repository.updateBottomBarRevealHandleHeight(action.heightDp)
                is ToggleGradientBackground -> repository.updateGradientBackgroundEnabled(action.enabled)
                is SetGradientBackgroundStrength -> repository.updateGradientBackgroundStrength(action.strength)
                is SetGradientBackgroundDirection -> repository.updateGradientBackgroundDirection(action.direction)
                is ToggleTransparentSearchCards -> repository.updateTransparentSearchCards(action.enabled)
                is ToggleTransparentPodcastCards -> repository.updateTransparentPodcastCards(action.enabled)
                is ToggleTransparentEpisodeRows -> repository.updateTransparentEpisodeRows(action.enabled)
                is ToggleTransparentCardsAndRows -> repository.updateTransparentCardsAndRows(action.enabled)
                is SetAutoDownloadLimit -> repository.updateAutoDownloadLimit(action.limit)
                is ToggleAutoRefreshOnStart -> repository.updateAutoRefreshOnStart(action.enabled)
                is SetMarkPlayedDuration -> repository.updateMarkPlayedDuration(action.seconds)
                is SetFeedUpdateMode -> repository.updateFeedUpdateMode(action.mode)
                is SetSmartStreamItemLimit -> repository.updateSmartStreamItemLimit(action.limit)

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
        }
    }
}
