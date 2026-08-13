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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
                homeBottomSpacing = 12,
                showMiniPlayerTimeOverlay = true,
                transparentMiniPlayer = true,
                transparentBottomBar = true,
                oneHandedMode = true,
                bottomBarCleanModeEnabled = true,
                bottomBarAutoHideEnabled = true,
                bottomBarAutoHideDelaySeconds = 9,
                bottomBarRevealHandleHeight = 84,
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
                smartStreamItemLimit = 37,
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
    fun concurrentIndependentSliderUpdates_persistEveryValueWithinBound() = runBlocking {
        withTimeout(5_000L) {
            val startGate = CompletableDeferred<Unit>()
            coroutineScope {
                listOf(
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateGridSize(5)
                    },
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateNavBarHeight(91)
                    },
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateProgressBarHeight(12)
                    },
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateHomeBottomSpacing(20)
                    },
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateBottomBarRevealHandleHeight(96)
                    },
                    async(Dispatchers.Default) {
                        startGate.await()
                        repository.updateGradientBackgroundStrength(0.75f)
                    }
                ).also { startGate.complete(Unit) }.awaitAll()
            }

            val persisted = repository.userSettingsFlow.first()
            assertEquals(5, persisted.gridSize)
            assertEquals(91, persisted.navBarHeight)
            assertEquals(12, persisted.progressBarHeight)
            assertEquals(20, persisted.homeBottomSpacing)
            assertEquals(96, persisted.bottomBarRevealHandleHeight)
            assertEquals(0.75f, persisted.gradientBackgroundStrength)
        }
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
