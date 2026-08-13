package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.RetryingDataFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Preferences.DATASTORE_NAME)
private val Keys = UserPreferenceKeys

@Singleton
class UserPreferencesRepositoryImpl private constructor(
    private val dataStore: DataStore<Preferences>,
    private val installationStateProvider: InstallationStateProvider
) : UserPreferencesRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        installationStateProvider: InstallationStateProvider
    ) : this(context.dataStore, installationStateProvider)

    private val migrationMutex = Mutex()

    @Volatile private var migrationComplete = false

    override val userSettingsFlow: Flow<UserSettings> =
        RetryingDataFlow.bounded(
            upstream = flow {
                ensureDefaultsMigrated()
                emitAll(dataStore.data)
            },
            shouldRetry = { it is IOException }
        ).map { prefs -> prefs.toUserSettings() }

    private suspend fun ensureDefaultsMigrated() {
        if (migrationComplete) return
        migrationMutex.withLock {
            if (migrationComplete) return
            editPreferences { preferences ->
                SettingsDefaultsMigration.apply(
                    preferences = preferences,
                    installationState = installationStateProvider.installationState()
                )
            }
            migrationComplete = true
        }
    }

    private suspend fun editPreferences(transform: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { preferences -> transform(preferences) }
    }

    override suspend fun updateTheme(theme: AppTheme) {
        editPreferences { it[Keys.THEME] = theme.name }
    }

    override suspend fun updateAppColor(color: AppColor) {
        editPreferences { it[Keys.APP_COLOR] = color.name }
    }

    override suspend fun updateColorStrength(strength: Float) {
        editPreferences { it[Keys.COLOR_STRENGTH] = strength }
    }

    override suspend fun updateBufferSettings(mode: BufferMode) {
        editPreferences { it[Keys.BUFFER_MODE] = mode.name }
    }

    override suspend fun updateLayoutMode(mode: LayoutMode) {
        editPreferences { it[Keys.LAYOUT_MODE] = mode.name }
    }

    override suspend fun updateGridSize(size: Int) {
        editPreferences { it[Keys.GRID_SIZE] = size }
    }

    override suspend fun updateShowGridTitles(show: Boolean) {
        editPreferences { it[Keys.SHOW_GRID_TITLES] = show }
    }

    override suspend fun updateConfirmDelete(confirm: Boolean) {
        editPreferences { it[Keys.CONFIRM_DELETE] = confirm }
    }

    override suspend fun updateProgressBarHeight(height: Int) {
        editPreferences { it[Keys.PROGRESS_BAR_HEIGHT] = height }
    }

    override suspend fun updateNavBarHeight(height: Int) {
        editPreferences { it[Keys.NAV_BAR_HEIGHT] = height }
    }

    override suspend fun updateHomeBottomSpacing(spacingDp: Int) {
        requireValidHomeBottomSpacing(spacingDp)
        editPreferences { it[Keys.HOME_BOTTOM_SPACING] = spacingDp }
    }

    override suspend fun updateShowMiniPlayerTimeOverlay(enabled: Boolean) {
        editPreferences { it[Keys.SHOW_MINI_PLAYER_TIME_OVERLAY] = enabled }
    }

    override suspend fun updateTransparentMiniPlayer(enabled: Boolean) {
        editPreferences { it[Keys.TRANSPARENT_MINI_PLAYER] = enabled }
    }

    override suspend fun updateTransparentBottomBar(enabled: Boolean) {
        editPreferences { it[Keys.TRANSPARENT_BOTTOM_BAR] = enabled }
    }

    override suspend fun updateOneHandedMode(enabled: Boolean) {
        editPreferences { it[Keys.ONE_HANDED_MODE] = enabled }
    }

    override suspend fun updateBottomBarCleanModeEnabled(enabled: Boolean) {
        editPreferences { it[Keys.BOTTOM_BAR_CLEAN_MODE_ENABLED] = enabled }
    }

    override suspend fun updateBottomBarAutoHideEnabled(enabled: Boolean) {
        editPreferences { it[Keys.BOTTOM_BAR_AUTO_HIDE_ENABLED] = enabled }
    }

    override suspend fun updateBottomBarAutoHideDelaySeconds(seconds: Int) {
        editPreferences { it[Keys.BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS] = seconds }
    }

    override suspend fun updateBottomBarRevealHandleHeight(heightDp: Int) {
        requireValidBottomBarRevealHandleHeight(heightDp)
        editPreferences { it[Keys.BOTTOM_BAR_REVEAL_HANDLE_HEIGHT] = heightDp }
    }

    override suspend fun updateGradientBackgroundEnabled(enabled: Boolean) {
        editPreferences { it[Keys.GRADIENT_BACKGROUND_ENABLED] = enabled }
    }

    override suspend fun updateGradientBackgroundStrength(strength: Float) {
        editPreferences { it[Keys.GRADIENT_BACKGROUND_STRENGTH] = strength.coerceIn(0f, 1f) }
    }

    override suspend fun updateGradientBackgroundDirection(direction: GradientDirection) {
        editPreferences { it[Keys.GRADIENT_BACKGROUND_DIRECTION] = direction.name }
    }

    override suspend fun updateTransparentSearchCards(enabled: Boolean) {
        editPreferences { it[Keys.TRANSPARENT_SEARCH_CARDS] = enabled }
    }

    override suspend fun updateTransparentPodcastCards(enabled: Boolean) {
        editPreferences { it[Keys.TRANSPARENT_PODCAST_CARDS] = enabled }
    }

    override suspend fun updateTransparentEpisodeRows(enabled: Boolean) {
        editPreferences { it[Keys.TRANSPARENT_EPISODE_ROWS] = enabled }
    }

    override suspend fun updateTransparentCardsAndRows(enabled: Boolean) {
        editPreferences {
            it[Keys.TRANSPARENT_SEARCH_CARDS] = enabled
            it[Keys.TRANSPARENT_PODCAST_CARDS] = enabled
            it[Keys.TRANSPARENT_EPISODE_ROWS] = enabled
        }
    }

    override suspend fun updateAutoDownloadLimit(limit: Int) {
        editPreferences { it[Keys.AUTO_DOWNLOAD_LIMIT] = limit }
    }

    override suspend fun updateAutoRefreshOnStart(enabled: Boolean) {
        editPreferences { it[Keys.AUTO_REFRESH_ON_START] = enabled }
    }

    override suspend fun updateBackgroundCheckEnabled(enabled: Boolean) {
        editPreferences { it[Keys.BACKGROUND_CHECK_ENABLED] = enabled }
    }

    override suspend fun updateBackgroundCheckInterval(hours: Int) {
        editPreferences { it[Keys.BACKGROUND_CHECK_INTERVAL] = hours }
    }

    override suspend fun updateMarkPlayedDuration(seconds: Int) {
        editPreferences { it[Keys.MARK_PLAYED_DURATION] = seconds }
    }

    override suspend fun updateFeedUpdateMode(mode: FeedUpdateMode) {
        editPreferences { it[Keys.FEED_UPDATE_MODE] = mode.name }
    }

    override suspend fun updateSmartStreamItemLimit(limit: Int) {
        requireValidSmartStreamItemLimit(limit)
        editPreferences { it[Keys.SMART_STREAM_ITEM_LIMIT] = limit }
    }

    override suspend fun updateIndicatorColor(colorArgb: Long) {
        editPreferences { it[Keys.INDICATOR_COLOR] = colorArgb }
    }

    override suspend fun updateIndicatorSize(sizeDp: Int) {
        editPreferences { it[Keys.INDICATOR_SIZE] = sizeDp }
    }

    override suspend fun updateIndicatorBorderWidth(widthDp: Int) {
        editPreferences { it[Keys.INDICATOR_BORDER] = widthDp }
    }

    override suspend fun updateIndicatorXOffset(offsetDp: Int) {
        editPreferences { it[Keys.INDICATOR_X_OFFSET] = offsetDp }
    }

    override suspend fun updateIndicatorYOffset(offsetDp: Int) {
        editPreferences { it[Keys.INDICATOR_Y_OFFSET] = offsetDp }
    }

    override suspend fun updateSaveToDownloadsFolder(enabled: Boolean) {
        editPreferences { it[Keys.SAVE_TO_DOWNLOADS_FOLDER] = enabled }
    }

    override suspend fun updateAutoCleanupEnabled(enabled: Boolean) {
        editPreferences { it[Keys.AUTO_CLEANUP_ENABLED] = enabled }
    }

    override suspend fun updateCleanupKeepLimit(limit: Int) {
        editPreferences { it[Keys.CLEANUP_KEEP_LIMIT] = limit }
    }

    override suspend fun updateCleanupIntervalHours(hours: Int) {
        editPreferences { it[Keys.CLEANUP_INTERVAL_HOURS] = hours }
    }

    override suspend fun restoreSettings(settings: UserSettings) {
        restoreSettingsOrThrow(settings)
    }

    override suspend fun restoreSettingsOrThrow(settings: UserSettings) {
        requireValidSmartStreamItemLimit(settings.smartStreamItemLimit)
        requireValidHomeBottomSpacing(settings.homeBottomSpacing)
        requireValidBottomBarRevealHandleHeight(settings.bottomBarRevealHandleHeight)
        editPreferences { prefs ->
            prefs[Keys.THEME] = settings.theme.name
            prefs[Keys.APP_COLOR] = settings.appColor.name
            prefs[Keys.COLOR_STRENGTH] = settings.colorStrength
            prefs[Keys.BUFFER_MODE] = settings.bufferMode.name

            prefs[Keys.LAYOUT_MODE] = settings.layoutMode.name

            prefs[Keys.GRID_SIZE] = settings.gridSize
            prefs[Keys.SHOW_GRID_TITLES] = settings.showGridTitles
            prefs[Keys.CONFIRM_DELETE] = settings.confirmDelete
            prefs[Keys.PROGRESS_BAR_HEIGHT] = settings.progressBarHeight
            prefs[Keys.NAV_BAR_HEIGHT] = settings.navBarHeight
            prefs[Keys.HOME_BOTTOM_SPACING] = settings.homeBottomSpacing
            prefs[Keys.SHOW_MINI_PLAYER_TIME_OVERLAY] = settings.showMiniPlayerTimeOverlay
            prefs[Keys.TRANSPARENT_MINI_PLAYER] = settings.transparentMiniPlayer
            prefs[Keys.TRANSPARENT_BOTTOM_BAR] = settings.transparentBottomBar
            prefs[Keys.ONE_HANDED_MODE] = settings.oneHandedMode
            prefs[Keys.BOTTOM_BAR_CLEAN_MODE_ENABLED] = settings.bottomBarCleanModeEnabled
            prefs[Keys.BOTTOM_BAR_AUTO_HIDE_ENABLED] = settings.bottomBarAutoHideEnabled
            prefs[Keys.BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS] = settings.bottomBarAutoHideDelaySeconds
            prefs[Keys.BOTTOM_BAR_REVEAL_HANDLE_HEIGHT] = settings.bottomBarRevealHandleHeight
            prefs[Keys.GRADIENT_BACKGROUND_ENABLED] = settings.gradientBackgroundEnabled
            prefs[Keys.GRADIENT_BACKGROUND_STRENGTH] = settings.gradientBackgroundStrength
            prefs[Keys.GRADIENT_BACKGROUND_DIRECTION] = settings.gradientBackgroundDirection.name
            prefs[Keys.TRANSPARENT_SEARCH_CARDS] = settings.transparentSearchCards
            prefs[Keys.TRANSPARENT_PODCAST_CARDS] = settings.transparentPodcastCards
            prefs[Keys.TRANSPARENT_EPISODE_ROWS] = settings.transparentEpisodeRows

            prefs[Keys.AUTO_DOWNLOAD_LIMIT] = settings.autoDownloadLimit
            prefs[Keys.AUTO_REFRESH_ON_START] = settings.autoRefreshOnStart
            prefs[Keys.BACKGROUND_CHECK_ENABLED] = settings.backgroundCheckEnabled
            prefs[Keys.BACKGROUND_CHECK_INTERVAL] = settings.backgroundCheckInterval

            prefs[Keys.MARK_PLAYED_DURATION] = settings.markPlayedDurationSeconds
            prefs[Keys.FEED_UPDATE_MODE] = settings.feedUpdateMode.name
            prefs[Keys.SMART_STREAM_ITEM_LIMIT] = settings.smartStreamItemLimit

            prefs[Keys.INDICATOR_COLOR] = settings.indicator.colorArgb
            prefs[Keys.INDICATOR_SIZE] = settings.indicator.size
            prefs[Keys.INDICATOR_BORDER] = settings.indicator.borderWidth
            prefs[Keys.INDICATOR_X_OFFSET] = settings.indicator.xOffset
            prefs[Keys.INDICATOR_Y_OFFSET] = settings.indicator.yOffset
            prefs[Keys.SAVE_TO_DOWNLOADS_FOLDER] = settings.saveToDownloadsFolder
            prefs[Keys.AUTO_CLEANUP_ENABLED] = settings.autoCleanupEnabled
            prefs[Keys.CLEANUP_KEEP_LIMIT] = settings.cleanupKeepLimit
            prefs[Keys.CLEANUP_INTERVAL_HOURS] = settings.cleanupIntervalHours
            prefs[SettingsDefaultsMigration.VERSION_KEY] = SettingsDefaultsMigration.CURRENT_VERSION
        }
        migrationComplete = true
    }

    override suspend fun clearSettings() {
        editPreferences { preferences ->
            preferences.clear()
            SettingsDefaultsMigration.apply(
                preferences = preferences,
                installationState = InstallationState.FRESH
            )
        }
        migrationComplete = true
    }

    internal companion object {
        fun createForTest(
            dataStore: DataStore<Preferences>,
            installationStateProvider: InstallationStateProvider
        ): UserPreferencesRepositoryImpl =
            UserPreferencesRepositoryImpl(dataStore, installationStateProvider)
    }
}

private fun requireValidSmartStreamItemLimit(limit: Int) {
    require(
        limit >= Constants.Preferences.MIN_SMART_STREAM_ITEM_LIMIT &&
            limit <= Constants.Preferences.MAX_SMART_STREAM_ITEM_LIMIT
    ) { "Smart Stream item limit must be between 0 and 100." }
}

private fun requireValidHomeBottomSpacing(spacingDp: Int) {
    require(
        spacingDp >= Constants.SettingsDefaults.MIN_HOME_BOTTOM_SPACING_DP.toInt() &&
            spacingDp <= Constants.SettingsDefaults.MAX_HOME_BOTTOM_SPACING_DP.toInt() &&
            spacingDp % Constants.SettingsDefaults.HOME_BOTTOM_SPACING_STEP_DP == 0
    ) { "Home bottom spacing must be a multiple of 4 between 0 and 24 dp." }
}

private fun requireValidBottomBarRevealHandleHeight(heightDp: Int) {
    val minimum = Constants.SettingsDefaults.MIN_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP.toInt()
    val maximum = Constants.SettingsDefaults.MAX_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP.toInt()
    val step = Constants.SettingsDefaults.BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_STEP_DP
    require(heightDp in minimum..maximum && (heightDp - minimum) % step == 0) {
        "Bottom bar reveal handle height must be a multiple of 4 between 24 and 120 dp."
    }
}
