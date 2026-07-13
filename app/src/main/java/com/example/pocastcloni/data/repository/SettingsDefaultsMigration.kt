package com.example.pocastcloni.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class InstallationState {
    FRESH,
    UPGRADE,
    UNKNOWN
}

fun interface InstallationStateProvider {
    fun installationState(): InstallationState
}

@Singleton
class PackageInstallationStateProvider
@Inject
constructor(
    @ApplicationContext private val context: Context
) : InstallationStateProvider {
    override fun installationState(): InstallationState =
        runCatching {
            val packageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            if (packageInfo.lastUpdateTime > packageInfo.firstInstallTime) {
                InstallationState.UPGRADE
            } else {
                InstallationState.FRESH
            }
        }.getOrDefault(InstallationState.UNKNOWN)
}

internal object SettingsDefaultsMigration {
    const val CURRENT_VERSION = 1
    const val FRESH_BACKGROUND_INTERVAL_HOURS = 6
    val VERSION_KEY = intPreferencesKey("settings_defaults_version")

    fun apply(
        preferences: MutablePreferences,
        installationState: InstallationState
    ) {
        if ((preferences[VERSION_KEY] ?: 0) >= CURRENT_VERSION) return

        val hasRestoredOrExistingSettings = preferences.asMap().keys.any { it != VERSION_KEY }
        val useLegacyDefaults =
            installationState == InstallationState.UPGRADE || hasRestoredOrExistingSettings

        if (preferences[UserPreferenceKeys.FEED_UPDATE_MODE] == null) {
            preferences[UserPreferenceKeys.FEED_UPDATE_MODE] =
                if (useLegacyDefaults) {
                    FeedUpdateMode.ALWAYS_FULL.name
                } else {
                    FeedUpdateMode.SMART_STREAM.name
                }
        }
        if (preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL] == null) {
            preferences[UserPreferenceKeys.BACKGROUND_CHECK_INTERVAL] =
                if (useLegacyDefaults) {
                    Constants.Preferences.DEFAULT_BACKGROUND_CHECK_INTERVAL
                } else {
                    FRESH_BACKGROUND_INTERVAL_HOURS
                }
        }
        if (preferences[UserPreferenceKeys.AUTO_REFRESH_ON_START] == null) {
            preferences[UserPreferenceKeys.AUTO_REFRESH_ON_START] =
                Constants.Preferences.DEFAULT_AUTO_REFRESH_ON_START
        }
        if (preferences[UserPreferenceKeys.BACKGROUND_CHECK_ENABLED] == null) {
            preferences[UserPreferenceKeys.BACKGROUND_CHECK_ENABLED] =
                Constants.Preferences.DEFAULT_BACKGROUND_CHECK_ENABLED
        }
        preferences[VERSION_KEY] = CURRENT_VERSION
    }
}
