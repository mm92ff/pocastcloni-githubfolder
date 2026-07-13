package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val repository by lazy {
        UserPreferencesRepositoryImpl(
            context = context,
            installationStateProvider = InstallationStateProvider { InstallationState.FRESH }
        )
    }

    @Before
    fun setUp() = runBlocking {
        repository.clearSettings()
    }

    @After
    fun tearDown() = runBlocking {
        repository.clearSettings()
    }

    @Test
    fun restoreSettings_roundTripsAllCurrentSettingsThroughDataStore() = runBlocking {
        val expected =
            UserSettings(
                theme = AppTheme.DARK,
                appColor = AppColor.TURQUOISE,
                colorStrength = 0.65f,
                bufferMode = BufferMode.MAXIMAL,
                layoutMode = LayoutMode.LIST,
                gridSize = 5,
                showGridTitles = false,
                confirmDelete = false,
                progressBarHeight = 11,
                navBarHeight = 92,
                showMiniPlayerTimeOverlay = true,
                transparentMiniPlayer = true,
                transparentBottomBar = true,
                oneHandedMode = true,
                bottomBarCleanModeEnabled = true,
                bottomBarAutoHideEnabled = true,
                bottomBarAutoHideDelaySeconds = 9,
                gradientBackgroundEnabled = true,
                gradientBackgroundStrength = 0.8f,
                gradientBackgroundDirection = GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT,
                transparentSearchCards = true,
                transparentPodcastCards = true,
                transparentEpisodeRows = true,
                autoDownloadLimit = 7,
                autoRefreshOnStart = false,
                backgroundCheckEnabled = false,
                backgroundCheckInterval = 12,
                markPlayedDurationSeconds = 45,
                feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                indicator =
                IndicatorSettings(
                    colorArgb = 0xFF123456,
                    size = 23,
                    borderWidth = 4,
                    xOffset = 13,
                    yOffset = -8
                ),
                saveToDownloadsFolder = true,
                autoCleanupEnabled = true,
                cleanupKeepLimit = 37,
                cleanupIntervalHours = 48
            )

        repository.restoreSettings(expected)

        assertEquals(expected, repository.userSettingsFlow.first())
    }

    @Test
    fun clearSettings_appliesCurrentFreshInstallFeedDefaults() = runBlocking {
        repository.restoreSettings(
            UserSettings(
                feedUpdateMode = FeedUpdateMode.ALWAYS_FULL,
                backgroundCheckInterval = 24
            )
        )

        repository.clearSettings()

        val settings = repository.userSettingsFlow.first()
        assertEquals(FeedUpdateMode.SMART_STREAM, settings.feedUpdateMode)
        assertEquals(6, settings.backgroundCheckInterval)
        assertEquals(true, settings.autoRefreshOnStart)
        assertEquals(true, settings.backgroundCheckEnabled)
    }
}
