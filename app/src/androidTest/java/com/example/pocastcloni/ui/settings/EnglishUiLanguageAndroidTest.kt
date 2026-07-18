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
    fun germanConfigurationUsesGermanResources() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.GERMANY)
        }
        val germanContext = targetContext.createConfigurationContext(configuration)

        // Exact German fixtures prove that the supported locale resolves its own catalog.
        assertEquals("1 Std. 05 Min.", formatEpisodeDuration(germanContext, 3_900_000L, Locale.GERMANY))
        assertEquals("5 Min.", formatEpisodeDuration(germanContext, 300_000L, Locale.GERMANY))
        assertEquals(
            "Kann RSS-Downloads vorzeitig beenden",
            germanContext.getString(R.string.settings_update_method_smart_stream_subtitle)
        )
        assertEquals("Feed-Leselimit", germanContext.getString(R.string.settings_smart_stream_item_limit))
        assertEquals(
            "Vollst\u00e4ndiger Feed",
            germanContext.getString(R.string.settings_smart_stream_item_limit_full)
        )
        assertEquals(
            "10 Eintr\u00e4ge",
            germanContext.getString(R.string.settings_smart_stream_item_limit_entries, 10)
        )
        assertEquals(
            "Der begrenzte Modus beendet das Lesen fr\u00fchzeitig in der Feed-Reihenfolge. " +
                "Bei unsortierten Feeds oder mehr neuen Episoden als dem Limit k\u00f6nnen " +
                "Episoden \u00fcbersehen werden.",
            germanContext.getString(R.string.settings_smart_stream_item_limit_warning)
        )
        assertEquals(
            "Alle Feeds vollst\u00e4ndig aktualisieren",
            germanContext.getString(R.string.settings_manual_full_refresh)
        )
        assertEquals(
            "Ignoriert das Feed-Leselimit und l\u00e4dt keine Audiodateien herunter.",
            germanContext.getString(R.string.settings_manual_full_refresh_subtitle)
        )
        assertEquals(
            "Podcast kon" + "nte nicht geladen werden. \u00dcberpr\u00fcfe die URL.",
            germanContext.getString(R.string.error_add_podcast_failed)
        )
        assertEquals(
            "Die ausgew\u00e4hlte Sicherung ist ung\u00fcltig oder wird nicht unterst\u00fctzt.",
            germanContext.getString(R.string.import_error_invalid_backup)
        )
        assertEquals(
            "Download-Speicherort",
            germanContext.getString(R.string.settings_section_download_location)
        )
        assertEquals(
            "Im Download-Ordner speichern",
            germanContext.getString(R.string.settings_save_to_downloads_folder)
        )
    }

    @Test
    fun unsupportedConfigurationFallsBackToEnglishDefaults() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fr-FR"))
        }
        val unsupportedContext = targetContext.createConfigurationContext(configuration)

        assertEquals(
            "Can stop RSS downloads early",
            unsupportedContext.getString(R.string.settings_update_method_smart_stream_subtitle)
        )
        assertEquals("Feed read limit", unsupportedContext.getString(R.string.settings_smart_stream_item_limit))
        assertEquals(
            "Refresh all feeds completely",
            unsupportedContext.getString(R.string.settings_manual_full_refresh)
        )
        assertEquals(
            "Could not load podcast. Check the URL.",
            unsupportedContext.getString(R.string.error_add_podcast_failed)
        )
        assertEquals(
            "The selected backup is invalid or unsupported.",
            unsupportedContext.getString(R.string.import_error_invalid_backup)
        )
    }
}
