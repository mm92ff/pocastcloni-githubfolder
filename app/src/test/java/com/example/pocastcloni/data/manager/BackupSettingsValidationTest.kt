package com.example.pocastcloni.data.manager

import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSettingsValidationTest {
    @Test
    fun `accepts defaults and supported boundary values`() {
        val limits = Constants.SecurityLimits

        validateBackupSettings(UserSettings())
        validateBackupSettings(
            UserSettings(
                colorStrength = 0f,
                gridSize = limits.MIN_BACKUP_GRID_SIZE,
                progressBarHeight = limits.MIN_BACKUP_UI_HEIGHT,
                navBarHeight = limits.MIN_BACKUP_UI_HEIGHT,
                homeBottomSpacing = limits.MIN_BACKUP_HOME_BOTTOM_SPACING,
                bottomBarAutoHideDelaySeconds = limits.MIN_BACKUP_AUTO_HIDE_SECONDS,
                bottomBarRevealHandleHeight = limits.MIN_BACKUP_REVEAL_HANDLE_HEIGHT,
                gradientBackgroundStrength = 0f,
                autoDownloadLimit = Constants.Preferences.NO_DOWNLOAD_LIMIT,
                backgroundCheckInterval = limits.MIN_BACKUP_BACKGROUND_INTERVAL_HOURS,
                markPlayedDurationSeconds = 0,
                indicator = IndicatorSettings(
                    colorArgb = 0L,
                    size = limits.MIN_BACKUP_INDICATOR_SIZE,
                    borderWidth = 0,
                    xOffset = -limits.MAX_BACKUP_INDICATOR_OFFSET_ABS,
                    yOffset = -limits.MAX_BACKUP_INDICATOR_OFFSET_ABS
                ),
                cleanupKeepLimit = 0,
                cleanupIntervalHours = limits.MIN_BACKUP_CLEANUP_INTERVAL_HOURS
            )
        )
        validateBackupSettings(
            UserSettings(
                colorStrength = 1f,
                gridSize = limits.MAX_BACKUP_GRID_SIZE,
                progressBarHeight = limits.MAX_BACKUP_UI_HEIGHT,
                navBarHeight = limits.MAX_BACKUP_UI_HEIGHT,
                homeBottomSpacing = limits.MAX_BACKUP_HOME_BOTTOM_SPACING,
                bottomBarAutoHideDelaySeconds = limits.MAX_BACKUP_AUTO_HIDE_SECONDS,
                bottomBarRevealHandleHeight = limits.MAX_BACKUP_REVEAL_HANDLE_HEIGHT,
                gradientBackgroundStrength = 1f,
                autoDownloadLimit = limits.MAX_BACKUP_AUTO_DOWNLOAD_LIMIT,
                backgroundCheckInterval = limits.MAX_BACKUP_BACKGROUND_INTERVAL_HOURS,
                markPlayedDurationSeconds = limits.MAX_BACKUP_MARK_PLAYED_SECONDS,
                indicator = IndicatorSettings(
                    colorArgb = 0xFFFF_FFFFL,
                    size = limits.MAX_BACKUP_INDICATOR_SIZE,
                    borderWidth = limits.MAX_BACKUP_INDICATOR_BORDER,
                    xOffset = limits.MAX_BACKUP_INDICATOR_OFFSET_ABS,
                    yOffset = limits.MAX_BACKUP_INDICATOR_OFFSET_ABS
                ),
                cleanupKeepLimit = limits.MAX_BACKUP_CLEANUP_KEEP_LIMIT,
                cleanupIntervalHours = limits.MAX_BACKUP_CLEANUP_INTERVAL_HOURS
            )
        )
    }

    @Test
    fun `rejects non-finite and out-of-range numeric settings`() {
        val defaults = UserSettings()
        val invalidSettings = listOf(
            defaults.copy(colorStrength = Float.NaN),
            defaults.copy(colorStrength = Float.POSITIVE_INFINITY),
            defaults.copy(colorStrength = -0.01f),
            defaults.copy(gridSize = 0),
            defaults.copy(progressBarHeight = 201),
            defaults.copy(navBarHeight = 0),
            defaults.copy(homeBottomSpacing = -4),
            defaults.copy(homeBottomSpacing = 2),
            defaults.copy(homeBottomSpacing = 28),
            defaults.copy(bottomBarAutoHideDelaySeconds = 301),
            defaults.copy(bottomBarRevealHandleHeight = 47),
            defaults.copy(bottomBarRevealHandleHeight = 50),
            defaults.copy(bottomBarRevealHandleHeight = 124),
            defaults.copy(gradientBackgroundStrength = Float.NEGATIVE_INFINITY),
            defaults.copy(gradientBackgroundStrength = 1.01f),
            defaults.copy(autoDownloadLimit = -1),
            defaults.copy(autoDownloadLimit = 101),
            defaults.copy(backgroundCheckInterval = 0),
            defaults.copy(markPlayedDurationSeconds = 3_601),
            defaults.copy(indicator = defaults.indicator.copy(colorArgb = -1L)),
            defaults.copy(indicator = defaults.indicator.copy(colorArgb = 0x1_0000_0000L)),
            defaults.copy(indicator = defaults.indicator.copy(size = 101)),
            defaults.copy(indicator = defaults.indicator.copy(borderWidth = 51)),
            defaults.copy(indicator = defaults.indicator.copy(xOffset = -101)),
            defaults.copy(indicator = defaults.indicator.copy(yOffset = 101)),
            defaults.copy(cleanupKeepLimit = 10_001),
            defaults.copy(cleanupIntervalHours = 8_761)
        )

        invalidSettings.forEach { settings ->
            assertThrows(IllegalArgumentException::class.java) {
                validateBackupSettings(settings)
            }
        }
    }
}
