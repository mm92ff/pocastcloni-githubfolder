package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
// TODO: ARCHITECTURE BOUNDARY VIOLATION - Data layer importing from UI layer
// These UI enums (AppTheme, AppColor, BufferMode) should be moved to domain.model
// to maintain proper dependency inversion. UI should depend on domain, not vice versa.
// FIXME: Move these enums to com.example.pocastcloni.domain.model and update all imports
import com.example.pocastcloni.ui.settings.AppColor
import com.example.pocastcloni.ui.settings.AppTheme
import com.example.pocastcloni.ui.settings.BufferMode
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Preferences.DATASTORE_NAME)

@Singleton
class UserPreferencesRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {
    private object Keys {
        val THEME = stringPreferencesKey(Constants.Preferences.KEY_THEME)
        val APP_COLOR = stringPreferencesKey(Constants.Preferences.KEY_APP_COLOR)
        val COLOR_STRENGTH = floatPreferencesKey(Constants.Preferences.KEY_COLOR_STRENGTH)
        val BUFFER_MODE = stringPreferencesKey(Constants.Preferences.KEY_BUFFER_MODE)

        val LAYOUT_MODE = stringPreferencesKey(Constants.Preferences.KEY_LAYOUT_MODE)

        val GRID_SIZE = intPreferencesKey(Constants.Preferences.KEY_GRID_SIZE)
        val SHOW_GRID_TITLES = booleanPreferencesKey(Constants.Preferences.KEY_SHOW_GRID_TITLES)
        val CONFIRM_DELETE = booleanPreferencesKey(Constants.Preferences.KEY_CONFIRM_DELETE)
        val PROGRESS_BAR_HEIGHT = intPreferencesKey(Constants.Preferences.KEY_PROGRESS_BAR_HEIGHT)
        val NAV_BAR_HEIGHT = intPreferencesKey(Constants.Preferences.KEY_NAV_BAR_HEIGHT)
        val ONE_HANDED_MODE = booleanPreferencesKey(Constants.Preferences.KEY_ONE_HANDED_MODE)

        val AUTO_DOWNLOAD_LIMIT = intPreferencesKey(Constants.Preferences.KEY_AUTO_DOWNLOAD_LIMIT)
        val AUTO_REFRESH_ON_START = booleanPreferencesKey(Constants.Preferences.KEY_AUTO_REFRESH_ON_START)
        val BACKGROUND_CHECK_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_BACKGROUND_CHECK_ENABLED)
        val BACKGROUND_CHECK_INTERVAL = intPreferencesKey(Constants.Preferences.KEY_BACKGROUND_CHECK_INTERVAL)

        val MARK_PLAYED_DURATION = intPreferencesKey(Constants.Preferences.KEY_MARK_PLAYED_DURATION)

        val FEED_UPDATE_MODE = stringPreferencesKey(Constants.KEY_FEED_UPDATE_MODE)

        val INDICATOR_COLOR = longPreferencesKey(Constants.Preferences.KEY_INDICATOR_COLOR)
        val INDICATOR_SIZE = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_SIZE)
        val INDICATOR_BORDER = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_BORDER)
        val INDICATOR_X_OFFSET = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_X_OFFSET)
        val INDICATOR_Y_OFFSET = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_Y_OFFSET)
        val SAVE_TO_DOWNLOADS_FOLDER = booleanPreferencesKey(Constants.Preferences.KEY_SAVE_TO_DOWNLOADS_FOLDER)
    }

    override val userSettingsFlow: Flow<UserSettings> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Timber.e(exception, "Error reading preferences.")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                val defaultSettings = UserSettings()

                val theme =
                    try {
                        AppTheme.valueOf(prefs[Keys.THEME] ?: defaultSettings.theme.name)
                    } catch (e: Exception) {
                        defaultSettings.theme
                    }
                val appColor =
                    try {
                        AppColor.valueOf(prefs[Keys.APP_COLOR] ?: defaultSettings.appColor.name)
                    } catch (
                        e: Exception
                    ) {
                        defaultSettings.appColor
                    }
                val bufferMode =
                    try {
                        BufferMode.valueOf(prefs[Keys.BUFFER_MODE] ?: defaultSettings.bufferMode.name)
                    } catch (
                        e: Exception
                    ) {
                        defaultSettings.bufferMode
                    }
                val feedMode =
                    try {
                        FeedUpdateMode.valueOf(prefs[Keys.FEED_UPDATE_MODE] ?: defaultSettings.feedUpdateMode.name)
                    } catch (
                        e: Exception
                    ) {
                        defaultSettings.feedUpdateMode
                    }

                // NEU: Layout Mode sicher laden
                val layoutMode =
                    try {
                        LayoutMode.valueOf(prefs[Keys.LAYOUT_MODE] ?: defaultSettings.layoutMode.name)
                    } catch (e: Exception) {
                        defaultSettings.layoutMode
                    }

                UserSettings(
                    theme = theme,
                    appColor = appColor,
                    colorStrength = prefs[Keys.COLOR_STRENGTH] ?: defaultSettings.colorStrength,
                    bufferMode = bufferMode,
                    // NEU: Zugewiesener Wert
                    layoutMode = layoutMode,
                    gridSize = prefs[Keys.GRID_SIZE] ?: defaultSettings.gridSize,
                    showGridTitles = prefs[Keys.SHOW_GRID_TITLES] ?: defaultSettings.showGridTitles,
                    confirmDelete = prefs[Keys.CONFIRM_DELETE] ?: defaultSettings.confirmDelete,
                    progressBarHeight = prefs[Keys.PROGRESS_BAR_HEIGHT] ?: defaultSettings.progressBarHeight,
                    navBarHeight = prefs[Keys.NAV_BAR_HEIGHT] ?: defaultSettings.navBarHeight,
                    oneHandedMode = prefs[Keys.ONE_HANDED_MODE] ?: defaultSettings.oneHandedMode,
                    autoDownloadLimit = prefs[Keys.AUTO_DOWNLOAD_LIMIT] ?: defaultSettings.autoDownloadLimit,
                    autoRefreshOnStart = prefs[Keys.AUTO_REFRESH_ON_START] ?: defaultSettings.autoRefreshOnStart,
                    backgroundCheckEnabled = prefs[Keys.BACKGROUND_CHECK_ENABLED] ?: defaultSettings.backgroundCheckEnabled,
                    backgroundCheckInterval = prefs[Keys.BACKGROUND_CHECK_INTERVAL] ?: defaultSettings.backgroundCheckInterval,
                    markPlayedDurationSeconds = prefs[Keys.MARK_PLAYED_DURATION] ?: defaultSettings.markPlayedDurationSeconds,
                    feedUpdateMode = feedMode,
                    indicator =
                    IndicatorSettings(
                        colorArgb = prefs[Keys.INDICATOR_COLOR] ?: defaultSettings.indicator.colorArgb,
                        size = prefs[Keys.INDICATOR_SIZE] ?: defaultSettings.indicator.size,
                        borderWidth = prefs[Keys.INDICATOR_BORDER] ?: defaultSettings.indicator.borderWidth,
                        xOffset = prefs[Keys.INDICATOR_X_OFFSET] ?: defaultSettings.indicator.xOffset,
                        yOffset = prefs[Keys.INDICATOR_Y_OFFSET] ?: defaultSettings.indicator.yOffset
                    ),
                    saveToDownloadsFolder = prefs[Keys.SAVE_TO_DOWNLOADS_FOLDER] ?: Constants.Preferences.DEFAULT_SAVE_TO_DOWNLOADS_FOLDER,
                )
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

    // NEU: Implementierung des Updates
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

    override suspend fun updateOneHandedMode(enabled: Boolean) {
        try {
            context.dataStore.edit { it[Keys.ONE_HANDED_MODE] = enabled }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist one handed mode preference")
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

    override suspend fun restoreSettings(settings: UserSettings) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.THEME] = settings.theme.name
                prefs[Keys.APP_COLOR] = settings.appColor.name
                prefs[Keys.COLOR_STRENGTH] = settings.colorStrength
                prefs[Keys.BUFFER_MODE] = settings.bufferMode.name

                // NEU: Layout Mode wiederherstellen
                prefs[Keys.LAYOUT_MODE] = settings.layoutMode.name

                prefs[Keys.GRID_SIZE] = settings.gridSize
                prefs[Keys.SHOW_GRID_TITLES] = settings.showGridTitles
                prefs[Keys.CONFIRM_DELETE] = settings.confirmDelete
                prefs[Keys.PROGRESS_BAR_HEIGHT] = settings.progressBarHeight
                prefs[Keys.NAV_BAR_HEIGHT] = settings.navBarHeight
                prefs[Keys.ONE_HANDED_MODE] = settings.oneHandedMode

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
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to restore settings to DataStore")
        }
    }

    override suspend fun clearSettings() {
        try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to clear settings from DataStore")
        }
    }
}
