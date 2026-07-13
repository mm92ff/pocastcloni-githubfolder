package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Preferences.DATASTORE_NAME)
private val Keys = UserPreferenceKeys

@Singleton
class UserPreferencesRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val installationStateProvider: InstallationStateProvider
) : UserPreferencesRepository {
    private val migrationMutex = Mutex()

    @Volatile private var migrationComplete = false

    override val userSettingsFlow: Flow<UserSettings> =
        flow {
            ensureDefaultsMigrated()
            emitAll(context.dataStore.data)
        }
            .catch { exception ->
                if (exception is IOException) {
                    Timber.e(exception, "Error reading preferences.")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs -> prefs.toUserSettings() }

    private suspend fun ensureDefaultsMigrated() {
        if (migrationComplete) return
        migrationMutex.withLock {
            if (migrationComplete) return
            context.dataStore.edit { preferences ->
                SettingsDefaultsMigration.apply(
                    preferences = preferences,
                    installationState = installationStateProvider.installationState()
                )
            }
            migrationComplete = true
        }
    }

    override suspend fun updateTheme(theme: AppTheme) {
        try {
            context.dataStore.edit { it[Keys.THEME] = theme.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist theme preference")
        }
    }

    override suspend fun updateAppColor(color: AppColor) {
        try {
            context.dataStore.edit { it[Keys.APP_COLOR] = color.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist app color preference")
        }
    }

    override suspend fun updateColorStrength(strength: Float) {
        try {
            context.dataStore.edit { it[Keys.COLOR_STRENGTH] = strength }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist color strength preference")
        }
    }

    override suspend fun updateBufferSettings(mode: BufferMode) {
        try {
            context.dataStore.edit { it[Keys.BUFFER_MODE] = mode.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist buffer settings preference")
        }
    }

    override suspend fun updateLayoutMode(mode: LayoutMode) {
        try {
            context.dataStore.edit { it[Keys.LAYOUT_MODE] = mode.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist layout mode preference")
        }
    }

    override suspend fun updateGridSize(size: Int) {
        try {
            context.dataStore.edit { it[Keys.GRID_SIZE] = size }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist grid size preference")
        }
    }

    override suspend fun updateShowGridTitles(show: Boolean) {
        try {
            context.dataStore.edit { it[Keys.SHOW_GRID_TITLES] = show }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist show grid titles preference")
        }
    }

    override suspend fun updateConfirmDelete(confirm: Boolean) {
        try {
            context.dataStore.edit { it[Keys.CONFIRM_DELETE] = confirm }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist confirm delete preference")
        }
    }

    override suspend fun updateProgressBarHeight(height: Int) {
        try {
            context.dataStore.edit { it[Keys.PROGRESS_BAR_HEIGHT] = height }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist progress bar height preference")
        }
    }

    override suspend fun updateNavBarHeight(height: Int) {
        try {
            context.dataStore.edit { it[Keys.NAV_BAR_HEIGHT] = height }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist nav bar height preference")
        }
    }

    override suspend fun updateShowMiniPlayerTimeOverlay(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.SHOW_MINI_PLAYER_TIME_OVERLAY] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist mini player time overlay preference")
        }
    }

    override suspend fun updateTransparentMiniPlayer(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.TRANSPARENT_MINI_PLAYER] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent mini player preference")
        }
    }

    override suspend fun updateTransparentBottomBar(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.TRANSPARENT_BOTTOM_BAR] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent bottom bar preference")
        }
    }

    override suspend fun updateOneHandedMode(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.ONE_HANDED_MODE] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist one handed mode preference")
        }
    }

    override suspend fun updateBottomBarCleanModeEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.BOTTOM_BAR_CLEAN_MODE_ENABLED] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist bottom bar clean mode preference")
        }
    }

    override suspend fun updateBottomBarAutoHideEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.BOTTOM_BAR_AUTO_HIDE_ENABLED] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist bottom bar auto-hide preference")
        }
    }

    override suspend fun updateBottomBarAutoHideDelaySeconds(seconds: Int) {
        try {
            context.dataStore.edit { it[Keys.BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS] = seconds }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist bottom bar auto-hide delay preference")
        }
    }

    override suspend fun updateGradientBackgroundEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.GRADIENT_BACKGROUND_ENABLED] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist gradient background preference")
        }
    }

    override suspend fun updateGradientBackgroundStrength(strength: Float) {
        try {
            context.dataStore.edit { it[Keys.GRADIENT_BACKGROUND_STRENGTH] = strength.coerceIn(0f, 1f) }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist gradient background strength preference")
        }
    }

    override suspend fun updateGradientBackgroundDirection(direction: GradientDirection) {
        try {
            context.dataStore.edit { it[Keys.GRADIENT_BACKGROUND_DIRECTION] = direction.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist gradient background direction preference")
        }
    }

    override suspend fun updateTransparentSearchCards(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.TRANSPARENT_SEARCH_CARDS] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent search cards preference")
        }
    }

    override suspend fun updateTransparentPodcastCards(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.TRANSPARENT_PODCAST_CARDS] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent podcast cards preference")
        }
    }

    override suspend fun updateTransparentEpisodeRows(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.TRANSPARENT_EPISODE_ROWS] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent episode rows preference")
        }
    }

    override suspend fun updateTransparentCardsAndRows(enabled: Boolean) {
        try {
            context.dataStore.edit {
                it[Keys.TRANSPARENT_SEARCH_CARDS] = enabled
                it[Keys.TRANSPARENT_PODCAST_CARDS] = enabled
                it[Keys.TRANSPARENT_EPISODE_ROWS] = enabled
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist transparent cards and rows preference")
        }
    }

    override suspend fun updateAutoDownloadLimit(limit: Int) {
        try {
            context.dataStore.edit { it[Keys.AUTO_DOWNLOAD_LIMIT] = limit }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist auto download limit preference")
        }
    }

    override suspend fun updateAutoRefreshOnStart(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.AUTO_REFRESH_ON_START] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist auto refresh on start preference")
        }
    }

    override suspend fun updateBackgroundCheckEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.BACKGROUND_CHECK_ENABLED] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist background check enabled preference")
        }
    }

    override suspend fun updateBackgroundCheckInterval(hours: Int) {
        try {
            context.dataStore.edit { it[Keys.BACKGROUND_CHECK_INTERVAL] = hours }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist background check interval preference")
        }
    }

    override suspend fun updateMarkPlayedDuration(seconds: Int) {
        try {
            context.dataStore.edit { it[Keys.MARK_PLAYED_DURATION] = seconds }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist mark played duration preference")
        }
    }

    override suspend fun updateFeedUpdateMode(mode: FeedUpdateMode) {
        try {
            context.dataStore.edit { it[Keys.FEED_UPDATE_MODE] = mode.name }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist feed update mode preference")
        }
    }

    override suspend fun updateIndicatorColor(colorArgb: Long) {
        try {
            context.dataStore.edit { it[Keys.INDICATOR_COLOR] = colorArgb }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist indicator color preference")
        }
    }

    override suspend fun updateIndicatorSize(sizeDp: Int) {
        try {
            context.dataStore.edit { it[Keys.INDICATOR_SIZE] = sizeDp }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist indicator size preference")
        }
    }

    override suspend fun updateIndicatorBorderWidth(widthDp: Int) {
        try {
            context.dataStore.edit { it[Keys.INDICATOR_BORDER] = widthDp }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist indicator border width preference")
        }
    }

    override suspend fun updateIndicatorXOffset(offsetDp: Int) {
        try {
            context.dataStore.edit { it[Keys.INDICATOR_X_OFFSET] = offsetDp }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist indicator X offset preference")
        }
    }

    override suspend fun updateIndicatorYOffset(offsetDp: Int) {
        try {
            context.dataStore.edit { it[Keys.INDICATOR_Y_OFFSET] = offsetDp }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist indicator Y offset preference")
        }
    }

    override suspend fun updateSaveToDownloadsFolder(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.SAVE_TO_DOWNLOADS_FOLDER] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist save_to_downloads_folder preference")
        }
    }

    override suspend fun updateAutoCleanupEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.AUTO_CLEANUP_ENABLED] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist auto_cleanup_enabled preference")
        }
    }

    override suspend fun updateCleanupKeepLimit(limit: Int) {
        try {
            context.dataStore.edit { it[Keys.CLEANUP_KEEP_LIMIT] = limit }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist cleanup_keep_limit preference")
        }
    }

    override suspend fun updateCleanupIntervalHours(hours: Int) {
        try {
            context.dataStore.edit { it[Keys.CLEANUP_INTERVAL_HOURS] = hours }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist cleanup_interval_hours preference")
        }
    }

    override suspend fun restoreSettings(settings: UserSettings) {
        try {
            restoreSettingsOrThrow(settings)
        } catch (e: IOException) {
            Timber.e(e, "Failed to restore settings to DataStore")
        }
    }

    override suspend fun restoreSettingsOrThrow(settings: UserSettings) {
        context.dataStore.edit { prefs ->
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
                prefs[Keys.SHOW_MINI_PLAYER_TIME_OVERLAY] = settings.showMiniPlayerTimeOverlay
                prefs[Keys.TRANSPARENT_MINI_PLAYER] = settings.transparentMiniPlayer
                prefs[Keys.TRANSPARENT_BOTTOM_BAR] = settings.transparentBottomBar
                prefs[Keys.ONE_HANDED_MODE] = settings.oneHandedMode
                prefs[Keys.BOTTOM_BAR_CLEAN_MODE_ENABLED] = settings.bottomBarCleanModeEnabled
                prefs[Keys.BOTTOM_BAR_AUTO_HIDE_ENABLED] = settings.bottomBarAutoHideEnabled
                prefs[Keys.BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS] = settings.bottomBarAutoHideDelaySeconds
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
        try {
            context.dataStore.edit { preferences ->
                preferences.clear()
                SettingsDefaultsMigration.apply(
                    preferences = preferences,
                    installationState = InstallationState.FRESH
                )
            }
            migrationComplete = true
        } catch (e: IOException) {
            Timber.e(e, "Failed to clear settings from DataStore")
        }
    }
}
