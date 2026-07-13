package com.example.pocastcloni.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDefaultsMigrationTest {
    @Test
    fun `fresh installation receives smart stream and six hours`() {
        val preferences = mutablePreferencesOf()

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.FRESH)

        assertEquals(FeedUpdateMode.SMART_STREAM.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
        assertEquals(6, preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL])
        assertEquals(true, preferences[UserPreferenceKeys.AUTO_REFRESH_ON_START])
        assertEquals(true, preferences[UserPreferenceKeys.BACKGROUND_CHECK_ENABLED])
        assertEquals(SettingsDefaultsMigration.CURRENT_VERSION, preferences[SettingsDefaultsMigration.VERSION_KEY])
        assertEquals(FeedUpdateMode.SMART_STREAM, preferences.toUserSettings().feedUpdateMode)
        assertEquals(6, preferences.toUserSettings().backgroundCheckInterval)
    }

    @Test
    fun `empty upgrade receives effective legacy values`() {
        val preferences = mutablePreferencesOf()

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.UPGRADE)

        assertEquals(FeedUpdateMode.ALWAYS_FULL.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
        assertEquals(
            Constants.Preferences.DEFAULT_BACKGROUND_CHECK_INTERVAL,
            preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL]
        )
    }

    @Test
    fun `restored partial settings preserve explicit fields and materialize missing legacy fields`() {
        val preferences =
            mutablePreferencesOf(
                UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL to 12,
                UserPreferenceKeys.AUTO_REFRESH_ON_START to false
            )

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.UNKNOWN)

        assertEquals(12, preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL])
        assertEquals(false, preferences[UserPreferenceKeys.AUTO_REFRESH_ON_START])
        assertEquals(FeedUpdateMode.ALWAYS_FULL.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
        assertEquals(true, preferences[UserPreferenceKeys.BACKGROUND_CHECK_ENABLED])
    }

    @Test
    fun `migration is idempotent after its version marker is written`() {
        val preferences = mutablePreferencesOf()
        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.FRESH)
        preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL] = 24
        preferences[UserPreferenceKeys.FEED_UPDATE_MODE] = FeedUpdateMode.ALWAYS_FULL.name

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.UPGRADE)

        assertEquals(24, preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL])
        assertEquals(FeedUpdateMode.ALWAYS_FULL.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
    }

    @Test
    fun `fresh reset can deliberately apply current defaults on an existing package`() {
        val preferences =
            mutablePreferencesOf(
                UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL to 1,
                UserPreferenceKeys.FEED_UPDATE_MODE to FeedUpdateMode.ALWAYS_FULL.name
            )
        preferences.clear()

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.FRESH)

        assertEquals(6, preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL])
        assertEquals(FeedUpdateMode.SMART_STREAM.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
    }

    @Test
    fun `unknown package state with empty preferences remains a fresh installation`() {
        val preferences = mutablePreferencesOf()

        SettingsDefaultsMigration.apply(preferences, installationState = InstallationState.UNKNOWN)

        assertEquals(6, preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL])
        assertEquals(FeedUpdateMode.SMART_STREAM.name, preferences[UserPreferenceKeys.FEED_UPDATE_MODE])
    }
}
