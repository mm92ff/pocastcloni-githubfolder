package com.example.pocastcloni.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pocastcloni.util.Constants

internal object UserPreferenceKeys {
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
    val SHOW_MINI_PLAYER_TIME_OVERLAY = booleanPreferencesKey(Constants.Preferences.KEY_SHOW_MINI_PLAYER_TIME_OVERLAY)
    val TRANSPARENT_MINI_PLAYER = booleanPreferencesKey(Constants.Preferences.KEY_TRANSPARENT_MINI_PLAYER)
    val TRANSPARENT_BOTTOM_BAR = booleanPreferencesKey(Constants.Preferences.KEY_TRANSPARENT_BOTTOM_BAR)
    val ONE_HANDED_MODE = booleanPreferencesKey(Constants.Preferences.KEY_ONE_HANDED_MODE)
    val BOTTOM_BAR_CLEAN_MODE_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_BOTTOM_BAR_CLEAN_MODE_ENABLED)
    val BOTTOM_BAR_AUTO_HIDE_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_BOTTOM_BAR_AUTO_HIDE_ENABLED)
    val BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS =
        intPreferencesKey(Constants.Preferences.KEY_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS)
    val GRADIENT_BACKGROUND_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_GRADIENT_BACKGROUND_ENABLED)
    val GRADIENT_BACKGROUND_STRENGTH = floatPreferencesKey(Constants.Preferences.KEY_GRADIENT_BACKGROUND_STRENGTH)
    val GRADIENT_BACKGROUND_DIRECTION = stringPreferencesKey(Constants.Preferences.KEY_GRADIENT_BACKGROUND_DIRECTION)
    val TRANSPARENT_SEARCH_CARDS = booleanPreferencesKey(Constants.Preferences.KEY_TRANSPARENT_SEARCH_CARDS)
    val TRANSPARENT_PODCAST_CARDS = booleanPreferencesKey(Constants.Preferences.KEY_TRANSPARENT_PODCAST_CARDS)
    val TRANSPARENT_EPISODE_ROWS = booleanPreferencesKey(Constants.Preferences.KEY_TRANSPARENT_EPISODE_ROWS)

    val AUTO_DOWNLOAD_LIMIT = intPreferencesKey(Constants.Preferences.KEY_AUTO_DOWNLOAD_LIMIT)
    val AUTO_REFRESH_ON_START = booleanPreferencesKey(Constants.Preferences.KEY_AUTO_REFRESH_ON_START)
    val BACKGROUND_CHECK_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_BACKGROUND_CHECK_ENABLED)
    val BACKGROUND_CHECK_INTERVAL = intPreferencesKey(Constants.Preferences.KEY_BACKGROUND_CHECK_INTERVAL)

    val MARK_PLAYED_DURATION = intPreferencesKey(Constants.Preferences.KEY_MARK_PLAYED_DURATION)

    val FEED_UPDATE_MODE = stringPreferencesKey(Constants.KEY_FEED_UPDATE_MODE)
    val SMART_STREAM_ITEM_LIMIT = intPreferencesKey(Constants.Preferences.KEY_SMART_STREAM_ITEM_LIMIT)

    val INDICATOR_COLOR = longPreferencesKey(Constants.Preferences.KEY_INDICATOR_COLOR)
    val INDICATOR_SIZE = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_SIZE)
    val INDICATOR_BORDER = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_BORDER)
    val INDICATOR_X_OFFSET = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_X_OFFSET)
    val INDICATOR_Y_OFFSET = intPreferencesKey(Constants.Preferences.KEY_INDICATOR_Y_OFFSET)
    val SAVE_TO_DOWNLOADS_FOLDER = booleanPreferencesKey(Constants.Preferences.KEY_SAVE_TO_DOWNLOADS_FOLDER)
    val AUTO_CLEANUP_ENABLED = booleanPreferencesKey(Constants.Preferences.KEY_AUTO_CLEANUP_ENABLED)
    val CLEANUP_KEEP_LIMIT = intPreferencesKey(Constants.Preferences.KEY_CLEANUP_KEEP_LIMIT)
    val CLEANUP_INTERVAL_HOURS = intPreferencesKey(Constants.Preferences.KEY_CLEANUP_INTERVAL_HOURS)
}
