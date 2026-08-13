package com.example.pocastcloni.sprint11

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.ReorderableLazyColumn
import com.example.pocastcloni.ui.common.ReorderableLazyVerticalGrid
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.ui.common.formatEpisodeDuration
import com.example.pocastcloni.ui.home.add.SearchArea
import com.example.pocastcloni.ui.home.detail.DownloadStatusUiModel
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.home.downloads.DownloadsContent
import com.example.pocastcloni.ui.home.downloads.DownloadsUiState
import com.example.pocastcloni.ui.main.BottomBarRevealHandle
import com.example.pocastcloni.ui.player.CustomProgressBar
import com.example.pocastcloni.ui.settings.ColorCircle
import com.example.pocastcloni.ui.settings.SettingsListContent
import com.example.pocastcloni.ui.settings.SettingsUiState
import com.example.pocastcloni.util.formatTime
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class Sprint11SemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun revealHandle_isNamedButtonWithTouchTargetAndClickAction() {
        var reveals by mutableIntStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                BottomBarRevealHandle(onReveal = { reveals++ })
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.desc_reveal_bottom_bar))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        composeRule.runOnIdle { assertEquals(1, reveals) }
    }

    @Test
    fun revealHandle_usesConfiguredTouchHeight() {
        composeRule.setContent {
            MaterialTheme {
                BottomBarRevealHandle(
                    onReveal = {},
                    handleHeight = 24.dp
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.desc_reveal_bottom_bar))
            .assertHeightIsEqualTo(24.dp)
    }

    @Test
    fun searchButton_keepsItsNameWhileLoading() {
        composeRule.setContent {
            MaterialTheme {
                SearchArea(
                    searchQuery = "query",
                    onSearchQueryChange = {},
                    isSearching = true,
                    onSearchTriggered = {},
                    searchError = null,
                    transparentSearchBar = false,
                    keyboardController = null
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.nav_search)).assertIsDisplayed()
    }

    @Test
    fun progressBar_exposesLocalizedRangeAndSetProgress() {
        var seekPosition by mutableStateOf<Long?>(null)
        composeRule.setContent {
            MaterialTheme {
                CustomProgressBar(
                    currentPositionMs = 25_000L,
                    bufferedPositionMs = 50_000L,
                    durationMs = 100_000L,
                    height = 4.dp,
                    color = Color.Red,
                    onSeek = { seekPosition = it },
                    modifier = Modifier.testTag("progress")
                )
            }
        }

        composeRule.onNodeWithTag("progress")
            .assertHeightIsAtLeast(48.dp)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(25_000f, 0f..100_000f)
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(
                        R.string.progress_position_description,
                        formatTime(25_000L, Locale.getDefault(Locale.Category.FORMAT)),
                        formatTime(100_000L, Locale.getDefault(Locale.Category.FORMAT))
                    )
                )
            )
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(50_000f) }

        composeRule.runOnIdle { assertEquals(50_000L, seekPosition) }
    }

    @Test
    fun progressBar_allowsCompactMiniPlayerHeight() {
        composeRule.setContent {
            MaterialTheme {
                CustomProgressBar(
                    currentPositionMs = 25_000L,
                    bufferedPositionMs = 50_000L,
                    durationMs = 100_000L,
                    height = 30.dp,
                    color = Color.Red,
                    onSeek = {},
                    modifier = Modifier.height(30.dp).testTag("compact-progress")
                )
            }
        }

        composeRule.onNodeWithTag("compact-progress").assertHeightIsEqualTo(30.dp)
    }

    @Test
    fun progressBar_clampsInvalidDurationAndRejectsSetProgress() {
        var seekPosition by mutableStateOf<Long?>(null)
        composeRule.setContent {
            MaterialTheme {
                CustomProgressBar(
                    currentPositionMs = 10_000L,
                    bufferedPositionMs = 20_000L,
                    durationMs = -1L,
                    height = 8.dp,
                    color = Color.Blue,
                    onSeek = { seekPosition = it },
                    modifier = Modifier.testTag("invalid-progress")
                )
            }
        }

        val semanticsNode = composeRule.onNodeWithTag("invalid-progress").fetchSemanticsNode()
        val accepted = semanticsNode.config[SemanticsActions.SetProgress].action?.invoke(1_000f) ?: false

        assertFalse(accepted)
        assertEquals(ProgressBarRangeInfo(0f, 0f..0f), semanticsNode.config[SemanticsProperties.ProgressBarRangeInfo])

        composeRule.onNodeWithTag("invalid-progress").performTouchInput {
            click()
            swipeRight()
        }
        composeRule.runOnIdle { assertEquals(null, seekPosition) }
    }

    @Test
    fun episodeDuration_usesNonUsFormatLocale() {
        val arabic = Locale.forLanguageTag("ar-EG")

        assertEquals(
            context.getString(
                R.string.episode_duration_hours_minutes,
                "\u0661",
                "\u0660\u0662"
            ),
            formatEpisodeDuration(context, durationMs = 3_720_000L, locale = arabic)
        )
    }

    @Test
    fun colorSwatch_reportsRadioRoleNameAndSelection() {
        composeRule.setContent {
            MaterialTheme {
                Row(Modifier.selectableGroup()) {
                    ColorCircle(
                        colorHex = 0xFF4CAF50,
                        colorName = context.getString(R.string.color_green),
                        isSelected = true,
                        onClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.color_green))
            .assertHeightIsAtLeast(48.dp)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun downloads_renderLoadingErrorEmptyAndRetainedContentSeparately() {
        var state by mutableStateOf(DownloadsUiState())
        var retries by mutableIntStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                DownloadsContent(
                    uiState = state,
                    innerPadding = PaddingValues(),
                    onRetry = { retries++ },
                    onPlayEpisode = {},
                    onFavoriteToggle = {},
                    onDeleteEpisode = {}
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            state =
                DownloadsUiState(
                    contentLoad = RetainedLoad(loading = false, error = UiText.StringResource(R.string.error_unknown)),
                    isLoading = false
                )
        }
        composeRule.onNodeWithText(context.getString(R.string.error_unknown)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }

        composeRule.runOnIdle {
            state =
                DownloadsUiState(
                    contentLoad = RetainedLoad(loading = false, lastValue = persistentListOf()),
                    isLoading = false
                )
        }
        composeRule.onNodeWithText(context.getString(R.string.no_downloads)).assertIsDisplayed()

        val episode = testEpisode()
        val episodes = persistentListOf(episode)
        composeRule.runOnIdle {
            state =
                DownloadsUiState(
                    contentLoad =
                    RetainedLoad(
                        loading = false,
                        lastValue = episodes,
                        error = UiText.StringResource(R.string.error_unknown)
                    ),
                    isLoading = false,
                    episodes = episodes
                )
        }
        composeRule.onNodeWithText(episode.title).assertIsDisplayed()
    }

    @Test
    fun reorderActions_includeTitleAndRespectBoundaries() {
        var items by mutableStateOf(persistentListOf("Alpha", "Beta"))
        composeRule.setContent {
            MaterialTheme {
                ReorderableLazyColumn(
                    items = items,
                    key = { it },
                    itemLabel = { it },
                    onReorder = { from, to ->
                        val reordered = items.toMutableList()
                        reordered.add(to, reordered.removeAt(from))
                        items = reordered.toPersistentList()
                    }
                ) { _, item, _ ->
                    Text(item)
                }
            }
        }

        val moveAlphaDown = context.getString(R.string.move_item_down, "Alpha")
        val alphaNode = composeRule.onNode(hasCustomAction(moveAlphaDown)).fetchSemanticsNode()
        val alphaActions = alphaNode.config[SemanticsActions.CustomActions]
        assertEquals(listOf(moveAlphaDown), alphaActions.map { it.label })

        composeRule.runOnIdle {
            alphaActions.single { it.label == moveAlphaDown }.action()
        }

        composeRule.runOnIdle { assertEquals(listOf("Beta", "Alpha"), items) }
    }

    @Test
    fun gridReorderActions_useTheExistingReorderCallback() {
        var items by mutableStateOf(persistentListOf("Grid Alpha", "Grid Beta"))
        composeRule.setContent {
            MaterialTheme {
                ReorderableLazyVerticalGrid(
                    items = items,
                    key = { it },
                    itemLabel = { it },
                    columns = GridCells.Fixed(2),
                    onReorder = { from, to ->
                        val reordered = items.toMutableList()
                        reordered.add(to, reordered.removeAt(from))
                        items = reordered.toPersistentList()
                    }
                ) { _, item, _ ->
                    Text(item)
                }
            }
        }

        val moveDown = context.getString(R.string.move_item_down, "Grid Alpha")
        val actions =
            composeRule.onNode(hasCustomAction(moveDown)).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        composeRule.runOnIdle {
            actions.single { it.label == moveDown }.action()
        }

        composeRule.runOnIdle { assertEquals(listOf("Grid Beta", "Grid Alpha"), items) }
    }

    @Test
    fun settingsScrollPosition_survivesRestorationAndOnlyDifferentTabsReset() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                Box(Modifier.height(320.dp)) {
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
        }

        repeat(5) {
            composeRule.onNodeWithTag("settings-list-design").performTouchInput { swipeUp() }
        }
        val beforeRestore = settingsScrollValue("settings-list-design")
        assertTrue("Expected settings to scroll", beforeRestore > 0f)

        clickSettingsTab(R.string.settings_tab_design)
        assertEquals(beforeRestore, settingsScrollValue("settings-list-design"), 1f)

        restorationTester.emulateSavedInstanceStateRestore()

        val afterRestore = settingsScrollValue("settings-list-design")
        assertEquals(beforeRestore, afterRestore, 1f)

        clickSettingsTab(R.string.settings_tab_playback)
        assertEquals(0f, settingsScrollValue("settings-list-playback"), 1f)

        repeat(5) {
            composeRule.onNodeWithTag("settings-list-playback").performTouchInput { swipeUp() }
        }
        assertTrue("Expected playback settings to scroll", settingsScrollValue("settings-list-playback") > 0f)

        clickSettingsTab(R.string.settings_tab_design)
        assertEquals(0f, settingsScrollValue("settings-list-design"), 1f)

        clickSettingsTab(R.string.settings_tab_playback)
        assertEquals(0f, settingsScrollValue("settings-list-playback"), 1f)
    }

    private fun clickSettingsTab(labelRes: Int) {
        composeRule.onNode(
            hasText(context.getString(labelRes)) and hasClickAction()
        ).performClick()
    }

    private fun settingsScrollValue(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private fun hasCustomAction(label: String): SemanticsMatcher =
        SemanticsMatcher("has custom action '$label'") { node ->
            runCatching { node.config[SemanticsActions.CustomActions] }
                .getOrDefault(emptyList())
                .any { it.label == label }
        }

    private fun testEpisode(): EpisodeUiModel =
        EpisodeUiModel(
            episodeId = 11L,
            guid = "episode-11",
            podcastUrl = "https://example.test/feed.xml",
            title = "Retained download",
            podcastTitle = "Sprint 11",
            imageUrl = null,
            downloadStatus = DownloadStatusUiModel.DOWNLOADED,
            downloadProgress = 1f,
            isPlayed = false,
            isFavorite = false,
            positionMs = 0L,
            description = null,
            podcastImageUrl = null,
            pubDateEpochMs = Instant.parse("2026-06-25T10:30:00Z").toEpochMilli(),
            durationMs = 42L * 60L * 1_000L
        )
}
