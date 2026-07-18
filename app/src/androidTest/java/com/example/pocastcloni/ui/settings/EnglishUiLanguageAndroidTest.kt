package com.example.pocastcloni.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.formatEpisodeDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class EnglishUiLanguageAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsDesignTabRendersEnglishSampleCopy() {
        composeRule.setContent {
            MaterialTheme {
                SettingsListContent(
                    settings = SettingsUiState.Success(),
                    downloadMessage = null,
                    isPlayerVisible = false,
                    onEvent = {},
                    onExportClick = {},
                    onImportClick = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Design").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Understanding AI").onFirst().performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI").assertIsDisplayed()
        composeRule.onAllNodesWithText("Understanding AI").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("AI as a security risk").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI as a security risk - How chatbots ...").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("KI verstehen").fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodesWithText("KI als Sicherheitsrisiko", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun settingsSyncSectionRendersEnglishSmartStreamCopy() {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SectionAutomation(
                        autoRefreshOnStart = false,
                        backgroundCheckEnabled = false,
                        backgroundCheckInterval = 12,
                        feedUpdateMode = FeedUpdateMode.SMART_STREAM,
                        smartStreamItemLimit = 10,
                        onToggleAutoRefreshOnStart = {},
                        onToggleBackgroundCheck = {},
                        onSetBackgroundCheckInterval = {},
                        onSetFeedUpdateMode = {},
                        onSetSmartStreamItemLimit = {},
                        onStartManualDownload = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Smart Stream (Fast)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Can stop RSS downloads early").assertIsDisplayed()
        composeRule.onNodeWithText("Feed read limit").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("10 entries").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Limited mode stops early in feed order. Unsorted feeds or more new episodes than the limit can cause episodes to be missed."
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Refresh all feeds completely").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ignores the feed read limit and downloads no audio.").assertIsDisplayed()
    }

    @Test
    fun addPodcastFailureSurfaceRendersStableEnglishCopy() {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SectionAddPodcast(
                        urlInput = "https://example.com/feed.xml",
                        isAdding = false,
                        allowInsecureHttp = false,
                        allowLocalNetwork = false,
                        message = UiText.StringResource(R.string.error_add_podcast_failed),
                        isError = true,
                        onUrlChange = {},
                        onAllowInsecureHttpChange = {},
                        onAllowLocalNetworkChange = {},
                        onAddClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Could not load podcast. Check the URL.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backupFailureDialogRendersStableEnglishCopy() {
        composeRule.setContent {
            MaterialTheme {
                HandleImportState(
                    state =
                    ImportUiState.Error(
                        UiText.StringResource(R.string.import_error_invalid_backup)
                    ),
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithText("Error").assertIsDisplayed()
        composeRule.onNodeWithText("The selected backup is invalid or unsupported.").assertIsDisplayed()
    }

    @Test
    fun germanConfigurationFallsBackToEnglishDefaults() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.GERMANY)
        }
        val germanContext = targetContext.createConfigurationContext(configuration)

        assertEquals("1h 05min", formatEpisodeDuration(germanContext, 3_900_000L, Locale.GERMANY))
        assertEquals("5 min", formatEpisodeDuration(germanContext, 300_000L, Locale.GERMANY))
        assertEquals(
            "Can stop RSS downloads early",
            germanContext.getString(R.string.settings_update_method_smart_stream_subtitle)
        )
        assertEquals("Feed read limit", germanContext.getString(R.string.settings_smart_stream_item_limit))
        assertEquals("Full feed", germanContext.getString(R.string.settings_smart_stream_item_limit_full))
        assertEquals("10 entries", germanContext.getString(R.string.settings_smart_stream_item_limit_entries, 10))
        assertEquals(
            "Limited mode stops early in feed order. Unsorted feeds or more new episodes than the limit can cause episodes to be missed.",
            germanContext.getString(R.string.settings_smart_stream_item_limit_warning)
        )
        assertEquals(
            "Refresh all feeds completely",
            germanContext.getString(R.string.settings_manual_full_refresh)
        )
        assertEquals(
            "Ignores the feed read limit and downloads no audio.",
            germanContext.getString(R.string.settings_manual_full_refresh_subtitle)
        )
        assertEquals("Could not load podcast. Check the URL.", germanContext.getString(R.string.error_add_podcast_failed))
        assertEquals(
            "The selected backup is invalid or unsupported.",
            germanContext.getString(R.string.import_error_invalid_backup)
        )
        assertEquals(
            "Download Location",
            germanContext.getString(R.string.settings_section_download_location)
        )
        assertEquals(
            "Save to Downloads folder",
            germanContext.getString(R.string.settings_save_to_downloads_folder)
        )
    }
}
